const {assertFails, assertSucceeds, initializeTestEnvironment} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, getDocs, collection, writeBatch, serverTimestamp, updateDoc, deleteDoc} = require('firebase/firestore');
const fs = require('fs');

describe('Owner catalogues and durable financial mutation protocol', function () {
    this.timeout(30000);
    let env, db;
    before(async () => {
        env = await initializeTestEnvironment({projectId: 'demo-clarity-final-audit', firestore: {
            host: '127.0.0.1', port: 8080, rules: fs.readFileSync('../firestore.rules', 'utf8')}});
    });
    beforeEach(async () => { await env.clearFirestore(); db = env.authenticatedContext('alice').firestore(); });
    after(async () => { await env?.cleanup(); });
    const path = (group, id) => `users/alice/${{EXPENSE:'expenses', INCOME:'incomeReceipts', CRYPTO:'cryptoResults',
        PROJECT:'expectedPayments', CATEGORY:'expenseCategories', SOURCE:'incomeSources'}[group]}/${id}`;
    const expense = (extra = {}) => ({amountMinor:85000, currency:'LKR', categoryId:'transport', paymentChannelId:'cash',
        spendingType:'discretionary', expenseDate:'2026-01-31', note:'', ...extra});
    const income = () => ({sourceId:'salary', receiptDate:'2026-01-31', currency:'LKR', originalAmountStr:'1000',
        lkrAmountMinor:100000, conversionBasis:'native_lkr', conversionRateStr:'1', note:''});
    const proof = (group, kind, id, payload, before = null, parentId = '') => ({group,kind,targetId:id,parentId,
        expectedUpdatedAt:before, payload, createdAt:serverTimestamp()});
    function batch(group, kind, id, payload, op, before = null) {
        const b = writeBatch(db), target = doc(db, path(group,id));
        if (kind === 'DELETE') b.delete(target);
        else if (kind === 'CREATE') b.set(target, {...payload, createdAt:serverTimestamp(), updatedAt:serverTimestamp(),
            ...(group==='CRYPTO' ? {lastOperationId:op} : {})});
        else b.update(target, {...payload, updatedAt:serverTimestamp(), ...(group==='CRYPTO' ? {lastOperationId:op} : {})});
        b.set(doc(db, `users/alice/syncOperations/${op}`), proof(group,kind,id,payload,before));
        b.set(doc(db, `users/alice/syncStates/${group}/records/${id}`), {operationId:op,deleted:kind==='DELETE',updatedAt:serverTimestamp()});
        return b;
    }
    async function baseline(group='EXPENSE', id='e', payload=expense()) {
        await assertSucceeds(batch(group,'CREATE',id,payload,'create_'+id).commit());
        return (await getDoc(doc(db,path(group,id)))).data();
    }
    it('allows audited owner expense create/edit/backdate/delete with immutable createdAt', async () => {
        const initial = await baseline();
        await assertSucceeds(batch('EXPENSE','UPDATE','e',expense({amountMinor:1000,expenseDate:'2025-12-31'}),'edit',initial.updatedAt).commit());
        const edited = (await getDoc(doc(db,path('EXPENSE','e')))).data();
        if (!edited.createdAt.isEqual(initial.createdAt)) throw Error('createdAt changed');
        await assertSucceeds(batch('EXPENSE','DELETE','e',{},'delete',edited.updatedAt).commit());
        if ((await getDoc(doc(db,path('EXPENSE','e')))).exists()) throw Error('Not deleted');
        if (!(await getDoc(doc(db,'users/alice/syncStates/EXPENSE/records/e'))).data().deleted) throw Error('Missing tombstone');
    });
    it('allows audited standalone income CRUD including explicit clearing of a historical rate', async () => {
        const initial = await baseline('INCOME','i',income());
        const foreign = {...income(),currency:'USD',originalAmountStr:'10',conversionBasis:'actual_credit',conversionRateStr:null,lkrAmountMinor:300000};
        await assertSucceeds(batch('INCOME','UPDATE','i',foreign,'edit',initial.updatedAt).commit());
        const after = (await getDoc(doc(db,path('INCOME','i')))).data();
        await assertSucceeds(batch('INCOME','DELETE','i',{},'delete',after.updatedAt).commit());
    });
    it('allows audited crypto CRUD without contributing to ordinary collections', async () => {
        const p = {direction:'loss',amountMinor:2500000,resultDate:'2026-01-31',note:''};
        const initial = await baseline('CRYPTO','c',p);
        await assertSucceeds(batch('CRYPTO','UPDATE','c',{...p,resultDate:'2025-12-31'},'edit',initial.updatedAt).commit());
        const edited = (await getDoc(doc(db,path('CRYPTO','c')))).data();
        await assertSucceeds(batch('CRYPTO','DELETE','c',{},'delete',edited.updatedAt).commit());
        for (const name of ['expenses','incomeReceipts','expectedPayments']) {
            if (!(await getDocs(collection(db,`users/alice/${name}`))).empty) throw Error('Unrelated money changed');
        }
    });
    it('rejects receipt-only, pointer-only, wrong payload and wrong target mutations', async () => {
        await assertFails(setDoc(doc(db,'users/alice/syncOperations/alone'),proof('EXPENSE','CREATE','e',expense())));
        await assertFails(setDoc(doc(db,'users/alice/syncStates/EXPENSE/records/e'),{operationId:'alone',deleted:false,updatedAt:serverTimestamp()}));
        const mismatch=batch('EXPENSE','CREATE','e',expense(),'bad');
        mismatch.set(doc(db,'users/alice/syncOperations/bad'),proof('EXPENSE','CREATE','other',expense()));
        await assertFails(mismatch.commit());
        const payload=batch('EXPENSE','CREATE','e',expense(),'bad2');
        payload.set(doc(db,'users/alice/syncOperations/bad2'),proof('EXPENSE','CREATE','e',expense({amountMinor:1})));
        await assertFails(payload.commit());
    });
    it('rejects replay, unaudited modifications and recreation of an acknowledged deleted identity', async () => {
        const initial=await baseline();
        await assertFails(updateDoc(doc(db,path('EXPENSE','e')),{amountMinor:1,updatedAt:serverTimestamp()}));
        await assertFails(batch('EXPENSE','UPDATE','e',expense(),'create_e',initial.updatedAt).commit());
        await assertSucceeds(batch('EXPENSE','DELETE','e',{},'delete',initial.updatedAt).commit());
        await assertFails(batch('EXPENSE','CREATE','e',expense(),'resurrect').commit());
        await assertFails(setDoc(doc(db,path('EXPENSE','e')),{...expense(),createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
        await assertFails(deleteDoc(doc(db,'users/alice/syncStates/EXPENSE/records/e')));
    });
    it('one receipt cannot authorize two expenses', async () => {
        const b=batch('EXPENSE','CREATE','e',expense(),'shared');
        b.set(doc(db,path('EXPENSE','other')),{...expense(),createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        b.set(doc(db,'users/alice/syncStates/EXPENSE/records/other'),{operationId:'shared',deleted:false,updatedAt:serverTimestamp()});
        await assertFails(b.commit());
    });
    it('rejects stale concurrent edits rather than last-write-wins', async () => {
        const initial=await baseline();
        const results=await Promise.allSettled([batch('EXPENSE','UPDATE','e',expense({amountMinor:100}),'a',initial.updatedAt).commit(),
            batch('EXPENSE','UPDATE','e',expense({amountMinor:200}),'b',initial.updatedAt).commit()]);
        if (results.filter(x=>x.status==='fulfilled').length!==1) throw Error('Exactly one expected-version edit must succeed');
    });
    it('acknowledged withdrawal fence prevents any outstanding attempt from committing', async () => {
        const b=writeBatch(db);
        b.set(doc(db,'users/alice/syncFences/op'),proof('EXPENSE','CREATE','e',expense()));
        b.set(doc(db,'users/alice/syncStates/EXPENSE/records/e'),{operationId:'op',deleted:true,updatedAt:serverTimestamp()});
        await assertSucceeds(b.commit());
        await assertFails(batch('EXPENSE','CREATE','e',expense(),'op').commit());
        await assertFails(batch('EXPENSE','CREATE','e',expense(),'another').commit());
    });
    it('a committed receipt prevents a contradictory withdrawal fence', async () => {
        await baseline();
        await assertFails(setDoc(doc(db,'users/alice/syncFences/create_e'),proof('EXPENSE','CREATE','e',expense())));
    });
    it('denies unauthenticated and cross-user reads/writes of catalogues, proofs, states and financial documents', async () => {
        await baseline();
        for (const client of [env.unauthenticatedContext().firestore(),env.authenticatedContext('bob').firestore()]) {
            for (const p of [path('EXPENSE','e'),'users/alice/syncOperations/create_e','users/alice/syncFences/x',
                'users/alice/syncStates/EXPENSE/records/e','users/alice/expenseCategories/custom_a','users/alice/incomeSources/custom_a']) {
                await assertFails(getDoc(doc(client,p))); await assertFails(setDoc(doc(client,p),{}));
            }
        }
    });
    it('custom categories can be added renamed and archived without rewriting financial history', async () => {
        const category={name:'Transport extras',archived:false,defaultValue:'discretionary'};
        const c=await baseline('CATEGORY','custom_a',category);
        await baseline('EXPENSE','e',expense({categoryId:'custom_a'}));
        await assertSucceeds(batch('CATEGORY','UPDATE','custom_a',{...category,name:'Travel',archived:true},'archive',c.updatedAt).commit());
        const e=(await getDoc(doc(db,path('EXPENSE','e')))).data();
        await assertFails(batch('EXPENSE','CREATE','new',expense({categoryId:'custom_a'}),'new').commit());
        await assertSucceeds(batch('EXPENSE','UPDATE','e',expense({categoryId:'custom_a',amountMinor:100}),'correct',e.updatedAt).commit());
        if ((await getDoc(doc(db,path('EXPENSE','e')))).data().categoryId!=='custom_a') throw Error('Historical ID changed');
        await assertFails(deleteDoc(doc(db,path('CATEGORY','custom_a'))));
    });
    it('custom and built-in sources preserve historical income and block future archived selection', async () => {
        for (const id of ['custom_source','salary']) {
            const source={name:'Renamed source',archived:false,defaultValue:'LKR'};
            const c=await baseline('SOURCE',id,source);
            await baseline('INCOME','i_'+id,{...income(),sourceId:id});
            await assertSucceeds(batch('SOURCE','UPDATE',id,{...source,archived:true},'archive_'+id,c.updatedAt).commit());
            await assertFails(batch('INCOME','CREATE','next_'+id,{...income(),sourceId:id},'next_'+id).commit());
            const old=(await getDoc(doc(db,path('INCOME','i_'+id)))).data();
            await assertSucceeds(batch('INCOME','UPDATE','i_'+id,{...income(),sourceId:id,note:'correction'},'correct_'+id,old.updatedAt).commit());
        }
    });
    it('strict catalogue fields types and immutable createdAt are enforced', async () => {
        const category={name:'A',archived:false,defaultValue:'committed'};
        for (const invalid of [{...category,name:''},{...category,archived:'false'},{...category,defaultValue:'LKR'},{...category,extra:true}]) {
            await assertFails(batch('CATEGORY','CREATE','custom_bad',invalid,'bad').commit());
        }
        await assertFails(batch('CATEGORY','CREATE','invented',category,'bad').commit());
        const old=await baseline('CATEGORY','custom_ok',category);
        const b=batch('CATEGORY','UPDATE','custom_ok',category,'edit',old.updatedAt);
        b.update(doc(db,path('CATEGORY','custom_ok')),{createdAt:serverTimestamp()});
        await assertFails(b.commit());
    });
    it('ordinary expenses reject impossible and future calendar dates while accepting leap-day backdates', async () => {
        for (const date of ['2025-02-29','2026-04-31','9999-12-31']) await assertFails(batch('EXPENSE','CREATE','bad',expense({expenseDate:date}),'bad').commit());
        await assertSucceeds(batch('EXPENSE','CREATE','leap',expense({expenseDate:'2024-02-29'}),'leap').commit());
    });
    it('ordinary income rejects impossible and future calendar dates', async () => {
        for (const date of ['2025-02-29','2026-04-31','9999-12-31']) await assertFails(batch('INCOME','CREATE','bad',{...income(),receiptDate:date},'bad').commit());
    });
    it('archiving a source does not trap receipts of an existing immutable-source agreement', async () => {
        const source={name:'Historical source',archived:false,defaultValue:'LKR'};
        const c=await baseline('SOURCE','custom_agreement',source);
        const p={type:'standalone',title:'Existing agreement',sourceId:'custom_agreement',currency:'LKR',dueDate:'2026-12-31',agreedAmountMinor:100000,
            allocatedAmountMinor:0,directReceivedAmountMinor:0,milestoneReceivedAmountMinor:0,isCancelled:false,note:'',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
        await assertSucceeds(setDoc(doc(db,path('PROJECT','p')),p));
        await assertSucceeds(batch('SOURCE','UPDATE','custom_agreement',{...source,archived:true},'archive',c.updatedAt).commit());
        const receipt={...income(),sourceId:'custom_agreement',originalAmountStr:'100',lkrAmountMinor:10000,originalAmountMinor:10000,linkedPaymentId:'p'};
        const b=writeBatch(db);
        b.set(doc(db,path('INCOME','r')),{...receipt,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        b.update(doc(db,path('PROJECT','p')),{directReceivedAmountMinor:10000,lastReceiptId:'r',updatedAt:serverTimestamp()});
        b.set(doc(db,'users/alice/syncOperations/r'),proof('RECEIPT','CREATE','r',{...receipt,linkedMilestoneId:null},null,'p'));
        b.set(doc(db,'users/alice/syncStates/INCOME/records/r'),{operationId:'r',deleted:false,updatedAt:serverTimestamp()});
        await assertSucceeds(b.commit());
        await assertFails(batch('INCOME','CREATE','new',{...income(),sourceId:'custom_agreement'},'new').commit());
    });
    it('audited parent creation, milestone allocation, receipt and partial cancellation retain the original financial protections', async () => {
        const agreement={type:'project',title:'Audit project',sourceId:'freelance',currency:'LKR',dueDate:'2026-12-31',agreedAmountMinor:100000,note:''};
        const create=writeBatch(db);
        create.set(doc(db,path('PROJECT','p')),{...agreement,allocatedAmountMinor:0,directReceivedAmountMinor:0,milestoneReceivedAmountMinor:0,
            isCancelled:false,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        create.set(doc(db,'users/alice/syncOperations/p'),proof('PROJECT','CREATE','p',agreement));
        create.set(doc(db,'users/alice/syncStates/PROJECT/records/p'),{operationId:'p',deleted:false,updatedAt:serverTimestamp()});
        await assertSucceeds(create.commit());
        const milestone={title:'M',dueDate:'2026-12-31',agreedAmountMinor:50000};
        const allocate=writeBatch(db);
        allocate.update(doc(db,path('PROJECT','p')),{allocatedAmountMinor:50000,lastMilestoneId:'m',lastMilestoneOpId:'allocate',updatedAt:serverTimestamp()});
        allocate.set(doc(db,'users/alice/expectedPayments/p/milestones/m'),{...milestone,receivedAmountMinor:0,isCancelled:false,lastMilestoneOpId:'allocate',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        allocate.set(doc(db,'users/alice/syncOperations/allocate'),proof('MILESTONE','CREATE','m',milestone,null,'p'));
        allocate.set(doc(db,'users/alice/syncStates/MILESTONE/parents/p/records/m'),{operationId:'allocate',deleted:false,updatedAt:serverTimestamp()});
        await assertSucceeds(allocate.commit());
        const receipt={...income(),sourceId:'freelance',originalAmountStr:'100',lkrAmountMinor:10000,originalAmountMinor:10000,linkedPaymentId:'p',linkedMilestoneId:'m'};
        const pay=writeBatch(db);
        pay.set(doc(db,path('INCOME','r')),{...receipt,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        pay.update(doc(db,path('PROJECT','p')),{milestoneReceivedAmountMinor:10000,lastReceiptId:'r',updatedAt:serverTimestamp()});
        pay.update(doc(db,'users/alice/expectedPayments/p/milestones/m'),{receivedAmountMinor:10000,lastReceiptId:'r',updatedAt:serverTimestamp()});
        pay.set(doc(db,'users/alice/syncOperations/pay'),proof('RECEIPT','CREATE','r',receipt,null,'p'));
        pay.set(doc(db,'users/alice/syncStates/INCOME/records/r'),{operationId:'pay',deleted:false,updatedAt:serverTimestamp()});
        await assertSucceeds(pay.commit());
        await assertFails(setDoc(doc(db,'users/alice/syncOperations/pay'),proof('RECEIPT','CREATE','r',receipt,null,'p')));
        const ms=(await getDoc(doc(db,'users/alice/expectedPayments/p/milestones/m'))).data();
        const cancel=writeBatch(db);
        cancel.update(doc(db,'users/alice/expectedPayments/p/milestones/m'),{isCancelled:true,lastMilestoneOpId:'cancel',updatedAt:serverTimestamp()});
        cancel.update(doc(db,path('PROJECT','p')),{allocatedAmountMinor:10000,lastMilestoneId:'m',lastMilestoneOpId:'cancel',updatedAt:serverTimestamp()});
        cancel.set(doc(db,'users/alice/syncOperations/cancel'),proof('MILESTONE','CANCEL','m',{},ms.updatedAt,'p'));
        cancel.set(doc(db,'users/alice/syncStates/MILESTONE/parents/p/records/m'),{operationId:'cancel',deleted:false,updatedAt:serverTimestamp()});
        await assertSucceeds(cancel.commit());
        const after=(await getDoc(doc(db,'users/alice/expectedPayments/p/milestones/m'))).data();
        if(after.agreedAmountMinor!==50000 || after.receivedAmountMinor!==10000) throw Error('Cancellation erased history');
        await assertFails(batch('INCOME','DELETE','r',{},'remove', (await getDoc(doc(db,path('INCOME','r')))).data().updatedAt).commit());
    });
    it('new protocol cannot bypass zero initial counters by creating a parent and allocation together', async () => {
        const p={type:'project',title:'P',sourceId:'freelance',currency:'LKR',dueDate:'2026-12-31',agreedAmountMinor:100000,note:''};
        const b=writeBatch(db);
        b.set(doc(db,path('PROJECT','p')),{...p,allocatedAmountMinor:50000,directReceivedAmountMinor:0,milestoneReceivedAmountMinor:0,
            isCancelled:false,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        b.set(doc(db,'users/alice/syncOperations/p'),proof('PROJECT','CREATE','p',p));
        b.set(doc(db,'users/alice/syncStates/PROJECT/records/p'),{operationId:'p',deleted:false,updatedAt:serverTimestamp()});
        b.set(doc(db,'users/alice/expectedPayments/p/milestones/m'),{title:'M',dueDate:'2026-12-31',agreedAmountMinor:50000,
            receivedAmountMinor:0,isCancelled:false,lastMilestoneOpId:'m',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        await assertFails(b.commit());
    });
    it('publication review: existing legacy milestone withdrawal barrier blocks the old cancellation but permits an audited replacement', async () => {
        async function seed(id) {
            await setDoc(doc(db,path('PROJECT',id)),{type:'project',title:'P',sourceId:'freelance',currency:'LKR',dueDate:'2026-12-31',
                agreedAmountMinor:100000,allocatedAmountMinor:0,directReceivedAmountMinor:0,milestoneReceivedAmountMinor:0,isCancelled:false,note:'',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
            const b=writeBatch(db);
            b.set(doc(db,`users/alice/expectedPayments/${id}/milestones/m`),{title:'M',dueDate:'2026-12-31',agreedAmountMinor:50000,receivedAmountMinor:0,
                isCancelled:false,lastMilestoneOpId:'seed',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
            b.update(doc(db,path('PROJECT',id)),{allocatedAmountMinor:50000,lastMilestoneId:'m',lastMilestoneOpId:'seed',updatedAt:serverTimestamp()});
            await assertSucceeds(b.commit());
        }
        function cancel(id,op) {
            const b=writeBatch(db);
            b.update(doc(db,`users/alice/expectedPayments/${id}/milestones/m`),{isCancelled:true,lastMilestoneOpId:op,updatedAt:serverTimestamp()});
            b.update(doc(db,path('PROJECT',id)),{allocatedAmountMinor:0,lastMilestoneId:'m',lastMilestoneOpId:op,updatedAt:serverTimestamp()});return b;
        }
        await seed('control');await assertSucceeds(cancel('control','old_control').commit());
        await seed('blocked');const original=(await getDoc(doc(db,'users/alice/expectedPayments/blocked/milestones/m'))).data();
        const barrier=writeBatch(db);
        barrier.set(doc(db,'users/alice/syncFences/old'),proof('MILESTONE','CANCEL','m',{},original.updatedAt,'blocked'));
        barrier.set(doc(db,'users/alice/syncStates/MILESTONE/parents/blocked/records/m'),{operationId:'old',deleted:false,updatedAt:serverTimestamp()});
        await assertSucceeds(barrier.commit());
        await assertFails(cancel('blocked','old').commit());
        const corrected=cancel('blocked','replacement');
        corrected.set(doc(db,'users/alice/syncOperations/replacement'),proof('MILESTONE','CANCEL','m',{},original.updatedAt,'blocked'));
        corrected.set(doc(db,'users/alice/syncStates/MILESTONE/parents/blocked/records/m'),{operationId:'replacement',deleted:false,updatedAt:serverTimestamp()});
        await assertSucceeds(corrected.commit());
    });
    it('publication review: crypto proof identity must match the operation marker on the business document', async () => {
        const value={direction:'gain',amountMinor:100,resultDate:'2026-01-31',note:''};
        await assertSucceeds(batch('CRYPTO','CREATE','control',value,'control').commit());
        const forged=batch('CRYPTO','CREATE','bad',value,'claimed');
        forged.set(doc(db,path('CRYPTO','bad')),{...value,lastOperationId:'different',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        await assertFails(forged.commit());
    });
    it('publication review: concurrent confirmation and withdrawal cannot both acknowledge the same operation', async () => {
        const withdrawal=writeBatch(db);
        withdrawal.set(doc(db,'users/alice/syncFences/race'),proof('EXPENSE','CREATE','race',expense()));
        withdrawal.set(doc(db,'users/alice/syncStates/EXPENSE/records/race'),{operationId:'race',deleted:true,updatedAt:serverTimestamp()});
        const outcomes=await Promise.allSettled([batch('EXPENSE','CREATE','race',expense(),'race').commit(),withdrawal.commit()]);
        if(outcomes.filter(x=>x.status==='fulfilled').length!==1) throw Error('Exactly one acknowledgement must win');
        const receipt=await getDoc(doc(db,'users/alice/syncOperations/race')),fence=await getDoc(doc(db,'users/alice/syncFences/race'));
        if(receipt.exists()===fence.exists()) throw Error('Receipt and fence must be mutually exclusive');
        if((await getDoc(doc(db,path('EXPENSE','race')))).exists()!==receipt.exists()) throw Error('Business mutation must match winning proof');
    });

    it('publication review: a fence without a record barrier is rejected while its atomic counterpart blocks legacy creation', async () => {
        const value=proof('EXPENSE','CREATE','guarded',expense());
        await assertFails(setDoc(doc(db,'users/alice/syncFences/guarded'),value));
        const valid=writeBatch(db);
        valid.set(doc(db,'users/alice/syncFences/guarded'),value);
        valid.set(doc(db,'users/alice/syncStates/EXPENSE/records/guarded'),{operationId:'guarded',deleted:true,updatedAt:serverTimestamp()});
        await assertSucceeds(valid.commit());
        await assertFails(setDoc(doc(db,path('EXPENSE','guarded')),{...expense(),createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
    });

    it('publication review: catalogue write isolation uses valid atomic owner counterparts rather than malformed payloads', async () => {
        const owner=env.authenticatedContext('alice').firestore();
        const denied=[env.authenticatedContext('bob').firestore(),env.unauthenticatedContext().firestore()];
        for(let i=0;i<denied.length;i++) for(const group of ['CATEGORY','SOURCE']) {
            const id=`custom_isolation_${group}_${i}`, op=`isolation_${group}_${i}`;
            const value={name:'Owner fixture',archived:false,defaultValue:group==='CATEGORY'?'committed':'LKR'};
            db=denied[i];await assertFails(batch(group,'CREATE',id,value,op).commit());
            db=owner;
            if((await getDoc(doc(db,path(group,id)))).exists()) throw Error('Denied operation wrote owner data');
            await assertSucceeds(batch(group,'CREATE',id,value,op).commit());
        }
    });

});
