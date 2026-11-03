// Isolated demo-emulator fixtures. Payloads and the receipt-first flow mirror FirestoreRecurringRemote.
const { assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, collection, getDoc, getDocs, query, where, orderBy, setDoc, updateDoc, deleteDoc, deleteField,
    writeBatch, runTransaction, serverTimestamp } = require('firebase/firestore');
const assert = require('assert');

module.exports = environment => describe('Recurring expense integrity', function () {
    const day = offset => new Date(Date.now() + 330 * 60000 + offset * 86400000).toISOString().slice(0, 10);
    const month = () => day(0).slice(0, 7);
    const db = uid => uid === null ? environment().unauthenticatedContext().firestore() : environment().authenticatedContext(uid || 'alice').firestore();
    const ref = (d, name, id, uid = 'alice') => doc(d, 'users', uid, name, id);
    const template = (d, id = 'rent', uid) => ref(d, 'recurringTemplates', id, uid);
    const operation = (d, id, uid) => ref(d, 'recurringOperations', id, uid);
    const occurrence = (d, id = `rent_${month()}`, uid) => ref(d, 'recurringOccurrences', id, uid);
    const expense = (d, id = `rec_rent_${month()}`, uid) => ref(d, 'expenses', id, uid);
    const config = (changes = {}) => ({ name: 'Rent fixture', categoryId: 'housing', spendingType: 'committed', paymentChannelId: 'cash',
        suggestedMinor: 3400000, firstDueDate: day(0), anchorDay: Number(day(0).slice(8)), cadence: 'monthly', ...changes });
    const values = data => Object.fromEntries(['name','categoryId','spendingType','paymentChannelId','suggestedMinor','firstDueDate','anchorDay','cadence','isCancelled'].map(k => [k, data[k]]));
    const actual = changes => ({ amountMinor: 3400000, currency: 'LKR', categoryId: 'housing', spendingType: 'committed', paymentChannelId: 'cash', expenseDate: day(0), note: '', ...changes });
    const cp = changes => ({ templateId: 'rent', month: month(), dueDate: day(0), templateVersion: 0, templateName: 'Rent fixture',
        expenseId: `rec_rent_${month()}`, actual: actual(), ...changes });
    function createWrites(d, id = 'rent', changes = {}, op = `create-${id}`) {
        const data = { ...config(changes), isCancelled: false, version: 0, lastOperationId: op, createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
        return [[template(d, id), data], [operation(d, op), { kind: 'CREATE', templateId: id, recordId: id, version: 0, payload: values(data), createdAt: serverTimestamp() }]];
    }
    function confirmWrites(d, payload = cp(), op = 'confirm') {
        const id = `${payload.templateId}_${payload.month}`;
        return [[occurrence(d, id), { ...payload, operationId: op, createdAt: serverTimestamp() }],
            [expense(d, payload.expenseId), { ...payload.actual, recurringOccurrenceId: id, createdAt: serverTimestamp(), updatedAt: serverTimestamp() }],
            [operation(d, op), { kind: 'CONFIRM', templateId: payload.templateId, recordId: id, version: payload.templateVersion, payload, createdAt: serverTimestamp() }]];
    }
    function batch(d, writes) { const b = writeBatch(d); for (const [r, data] of writes) b.set(r, data); return b.commit(); }
    const create = (d, id, changes) => batch(d, createWrites(d, id, changes));
    async function change(d, changes = {}, cancel = false, id = 'rent') {
        const old = (await getDoc(template(d, id))).data();
        const version = old.version + 1, op = `change-${id}-${version}`;
        const data = { ...old, ...changes, isCancelled: cancel, version, lastOperationId: op, updatedAt: serverTimestamp() };
        return batch(d, [[template(d, id), data], [operation(d, op), { kind: cancel ? 'CANCEL' : 'EDIT', templateId: id, recordId: id,
            version, payload: values(data), createdAt: serverTimestamp() }]]);
    }
    async function confirm(d, payload = cp(), op = 'confirm') {
        const id = `${payload.templateId}_${payload.month}`;
        return runTransaction(d, async tx => {
            const accepted = await tx.get(occurrence(d, id));
            if (accepted.exists()) {
                const data = accepted.data(); delete data.operationId; delete data.createdAt;
                assert.deepStrictEqual(data, payload, 'Existing confirmation payload conflict');
                return 'reconciled';
            }
            await tx.get(operation(d, op));
            const parent = (await tx.get(template(d, payload.templateId))).data();
            assert(parent && !parent.isCancelled && parent.version === payload.templateVersion, 'Template version/cancellation conflict');
            await tx.get(expense(d, payload.expenseId));
            for (const [r, data] of confirmWrites(d, payload, op)) tx.set(r, data);
            return 'confirmed';
        });
    }
    it('AT09 rent confirms once across retries and clients, ignored utilities add nothing, edited actual amount is exact', async () => {
        const d = db(); await assertSucceeds(create(d));
        await assertSucceeds(create(d, 'utilities', { name: 'Utilities fixture', categoryId: 'utilities', suggestedMinor: 500000 }));
        assert.equal((await getDocs(collection(d, 'users/alice/expenses'))).size, 0);
        await assertSucceeds(confirm(d)); await assertSucceeds(confirm(d, cp(), 'another-client'));
        assert.equal((await getDocs(collection(d, 'users/alice/expenses'))).size, 1);
        assert.equal((await getDoc(expense(d))).data().amountMinor, 3400000);
        const edited = cp({ templateId: 'utilities', templateName: 'Utilities fixture', expenseId: `rec_utilities_${month()}`,
            actual: actual({ amountMinor: 625050, categoryId: 'utilities', paymentChannelId: 'primary_bank_card', note: 'Actual bill' }) });
        await assertSucceeds(confirm(d, edited, 'utilities-paid')); await assertSucceeds(confirm(d, edited, 'utilities-retry'));
        const rows = (await getDocs(collection(d, 'users/alice/expenses'))).docs.map(row => row.data());
        assert.equal(rows.length, 2); assert.equal(rows.reduce((n, row) => n + row.amountMinor, 0), 4025050);
        for (const name of ['incomeReceipts','cryptoResults','expectedPayments']) assert.equal((await getDocs(collection(d, `users/alice/${name}`))).size, 0);
        assert.equal((await getDoc(ref(d, 'savingsGoals', 'active'))).exists(), false);
    });
    it('denies occurrence-only linked-expense-only and receipt-only confirmations', async () => {
        const d = db(); await create(d);
        for (const write of confirmWrites(d)) await assertFails(batch(d, [write]));
        assert.equal((await getDoc(occurrence(d))).exists(), false); assert.equal((await getDoc(expense(d))).exists(), false);
    });
    it('denies template creation plus confirmation even with matching after-state', async () => {
        const d = db(); await assertFails(batch(d, [...createWrites(d), ...confirmWrites(d)]));
    });
    it('denies mismatched actual fields IDs owners and multiple expenses sharing a confirmation', async () => {
        const d = db(); await create(d);
        let writes = confirmWrites(d); writes[1][1].amountMinor++; await assertFails(batch(d, writes));
        await assertFails(batch(d, confirmWrites(d, cp({ expenseId: 'different-id' }))));
        await assertFails(batch(d, confirmWrites(d, cp()).map(([r, data], i) => i === 1 ? [expense(d, cp().expenseId, 'bob'), data] : [r, data])));
        writes = confirmWrites(d); writes.push([expense(d, 'rec_extra'), { ...writes[1][1] }]); await assertFails(batch(d, writes));
        writes = confirmWrites(d); writes[0][1].templateId = 'other'; await assertFails(batch(d, writes));
    });
    it('one immutable operation cannot confirm multiple occurrences or templates', async () => {
        const d = db(); await create(d); await create(d, 'utilities');
        const second = cp({ templateId: 'utilities', expenseId: `rec_utilities_${month()}` });
        await assertFails(batch(d, [...confirmWrites(d), ...confirmWrites(d, second).slice(0, 2)]));
        await confirm(d);
        await assertFails(batch(d, confirmWrites(d, second)));
    });
    it('simultaneous matching clients create one expense and both reconcile safely', async () => {
        const d = db(); await create(d);
        const results = await Promise.allSettled([confirm(d, cp(), 'client-a'), confirm(d, cp(), 'client-b')]);
        assert(results.some(r => r.status === 'fulfilled'));
        // The SDK may surface a rule denial before retrying a stale create callback. The production
        // repository retains uncertainty, then reconciles this same payload rather than duplicating it.
        for (let i = 0; i < results.length; i++) if (results[i].status === 'rejected') {
            assert.equal(results[i].reason.code, 'permission-denied');
            await assertSucceeds(confirm(d, cp(), i === 0 ? 'client-a' : 'client-b'));
        }
        assert.equal((await getDocs(collection(d, 'users/alice/recurringOccurrences'))).size, 1);
        assert.equal((await getDocs(collection(d, 'users/alice/expenses'))).size, 1);
        assert.equal((await getDocs(collection(d, 'users/alice/recurringOperations'))).size, 2);
    });
    it('simultaneous different payloads expose conflict and cannot overwrite the winning expense', async () => {
        const d = db(); await create(d);
        const results = await Promise.allSettled([confirm(d, cp(), 'client-a'), confirm(d, cp({ actual: actual({ amountMinor: 3500000 }) }), 'client-b')]);
        assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
        assert.equal((await getDocs(collection(d, 'users/alice/expenses'))).size, 1);
        const record = (await getDoc(occurrence(d))).data(); assert.equal((await getDoc(expense(d))).data().amountMinor, record.actual.amountMinor);
    });
    it('correction category backdating note removal and deletion retain consumed history and never recreate on retry', async () => {
        const d = db(); await create(d); await confirm(d);
        const original = (await getDoc(occurrence(d))).data();
        await assertSucceeds(updateDoc(expense(d), { amountMinor: 3300000, categoryId: 'utilities', spendingType: 'discretionary',
            paymentChannelId: 'primary_bank_card', expenseDate: '2024-12-31', note: deleteField(), updatedAt: serverTimestamp() }));
        assert.deepStrictEqual((await getDoc(occurrence(d))).data(), original);
        await confirm(d); assert.equal((await getDoc(expense(d))).data().amountMinor, 3300000);
        await assertSucceeds(deleteDoc(expense(d))); await confirm(d);
        assert.equal((await getDoc(expense(d))).exists(), false); assert.equal((await getDoc(occurrence(d))).exists(), true);
        await assertFails(setDoc(expense(d), { ...actual(), recurringOccurrenceId: cp().templateId + '_' + month(), createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
        await assertFails(setDoc(expense(d), { ...actual(), createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    });
    it('link stripping forged linkage and changing immutable createdAt are denied while legacy omitted notes remain valid', async () => {
        const d = db(); await create(d); await confirm(d);
        await assertFails(updateDoc(expense(d), { recurringOccurrenceId: deleteField(), updatedAt: serverTimestamp() }));
        await assertFails(updateDoc(expense(d), { recurringOccurrenceId: 'other_' + month(), updatedAt: serverTimestamp() }));
        await assertFails(updateDoc(expense(d), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
        const legacy = actual(); delete legacy.note;
        await assertSucceeds(setDoc(expense(d, 'legacy'), { ...legacy, createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
        await assertFails(updateDoc(expense(d, 'legacy'), { recurringOccurrenceId: cp().templateId + '_' + month(), updatedAt: serverTimestamp() }));
    });
    it('template edits preserve first month and confirmed expense while stale confirmations are rejected', async () => {
        const d = db(); await create(d); await confirm(d);
        await assertSucceeds(change(d, { suggestedMinor: 3500000, anchorDay: 31, name: 'Edited rent' }));
        await assertFails(change(d, { firstDueDate: day(-1) }));
        await assertSucceeds(confirm(d)); assert.equal((await getDoc(expense(d))).data().amountMinor, 3400000);
        const second = cp({ templateId: 'utilities', expenseId: `rec_utilities_${month()}` });
        await create(d, 'utilities'); await change(d, { suggestedMinor: 999 }, false, 'utilities');
        await assertFails(batch(d, confirmWrites(d, second, 'stale-confirm')));
    });
    it('cancellation rejects due unconfirmed items and preserves recorded spending and matching retries', async () => {
        const d = db(); await create(d); await confirm(d); await change(d, {}, true);
        await assertSucceeds(confirm(d)); assert.equal((await getDoc(expense(d))).data().amountMinor, 3400000);
        await create(d, 'utilities'); await change(d, {}, true, 'utilities');
        await assertFails(batch(d, confirmWrites(d, cp({ templateId: 'utilities', expenseId: `rec_utilities_${month()}` }), 'cancelled-confirm')));
        await assertFails(change(d, { name: 'Uncancel' }));
        await assertFails(updateDoc(template(d), { isCancelled: false, version: 2, updatedAt: serverTimestamp() }));
    });
    it('cancellation racing an outstanding confirmation prevents its later write without removing prior spending', async () => {
        const d = db(); await create(d);
        let release, read; const gate = new Promise(r => release = r), started = new Promise(r => read = r);
        const pending = runTransaction(d, async tx => {
            await tx.get(occurrence(d)); await tx.get(template(d)); read(); await gate;
            for (const [r, data] of confirmWrites(d)) tx.set(r, data);
        });
        try { await started; await change(d, {}, true); } finally { release(); }
        await assertFails(pending); assert.equal((await getDoc(expense(d))).exists(), false);
    });
    it('verified absence retries when an earlier confirmation commits before rejection verification', async () => {
        const d = db(); await create(d);
        let release, read, first = true; const gate = new Promise(r => release = r), started = new Promise(r => read = r);
        const proof = runTransaction(d, async tx => {
            const accepted = await tx.get(occurrence(d));
            if (accepted.exists()) return 'confirmed';
            await tx.get(template(d));
            if (first) { first = false; read(); await gate; }
            return 'rejected';
        });
        try { await started; await confirm(d); } finally { release(); }
        assert.equal(await proof, 'confirmed'); assert.equal((await getDocs(collection(d, 'users/alice/expenses'))).size, 1);
    });
    it('strict template schema rejects invalid amounts dates classifications cadence counters and timestamps', async () => {
        const d = db();
        for (const bad of [{ name: '' }, { name: '  ' }, { name: 'x'.repeat(101) }, { suggestedMinor: 0 }, { suggestedMinor: -1 }, { suggestedMinor: 1.5 },
            { categoryId: 'fake' }, { paymentChannelId: 'fake' }, { spendingType: 'fake' }, { cadence: 'weekly' }, { anchorDay: 0 }, { anchorDay: 32 },
            { anchorDay: 1.5 }, { firstDueDate: '1900-02-29' }, { firstDueDate: '2025-02-29' }, { firstDueDate: '0000-01-01' }, { extra: true }])
            await assertFails(create(d, 'rent', bad));
        await assertSucceeds(create(d, 'rent', { firstDueDate: '2000-02-29' }));
        await assertFails(updateDoc(template(d), { version: 1, updatedAt: serverTimestamp() }));
        await assertFails(change(d, { createdAt: serverTimestamp() }));
    });
    it('strict confirmations enforce calendar actual date Colombo future boundary note and amount types', async () => {
        const d = db(); await create(d);
        for (const bad of [{ amountMinor: 0 }, { amountMinor: -1 }, { amountMinor: 1.5 }, { expenseDate: '2025-02-29' }, { expenseDate: day(1) },
            { note: null }, { note: 'x'.repeat(501) }, { currency: 'USD' }, { categoryId: 'fake' }, { extra: true }])
            await assertFails(batch(d, confirmWrites(d, cp({ actual: actual(bad) }))));
        await assertFails(batch(d, confirmWrites(d, cp({ dueDate: day(1) }))));
        await assertFails(batch(d, confirmWrites(d, cp({ templateVersion: 1 }))));
        await assertFails(batch(d, confirmWrites(d, cp({ templateName: 'Forged' }))));
        await assertSucceeds(confirm(d));
        await assertFails(updateDoc(expense(d), { expenseDate: day(1), updatedAt: serverTimestamp() }));
        await assertFails(updateDoc(expense(d), { expenseDate: '2025-02-29', updatedAt: serverTimestamp() }));
    });
    it('server schedule preserves leap February and original anchor rather than cumulative drift', async () => {
        const d = db(); await create(d, 'rent', { firstDueDate: '2024-01-31', anchorDay: 31 });
        for (const [m, due] of [['2024-02','2024-02-29'], ['2024-03','2024-03-31'], ['2025-02','2025-02-28'], ['2025-03','2025-03-31']]) {
            const p = cp({ month: m, dueDate: due, expenseId: `rec_rent_${m}`, actual: actual({ expenseDate: due }) });
            await assertSucceeds(confirm(d, p, 'confirm-' + m));
        }
        await assertFails(batch(d, confirmWrites(d, cp({ month: '2024-04', dueDate: '2024-04-29', expenseId: 'rec_rent_2024-04' }), 'drift')));
        const history = await getDocs(query(collection(d, 'users/alice/recurringOccurrences'), where('templateId','==','rent'),
            where('month','>=','2024-01'), where('month','<','2025-01'), orderBy('month')));
        assert.equal(history.size, 2);
    });
    it('immutable receipts confirmations and template history cannot be deleted or rewritten', async () => {
        const d = db(); await create(d); await confirm(d);
        for (const r of [occurrence(d), operation(d, 'confirm'), operation(d, 'create-rent')]) {
            await assertFails(deleteDoc(r)); await assertFails(updateDoc(r, { extra: true }));
        }
        await assertFails(deleteDoc(template(d)));
    });
    it('owner-only valid CRUD and full cross-user unauthenticated atomic operations are enforced', async () => {
        const d = db(); await create(d);
        for (const other of [db('bob'), db(null)]) {
            for (const name of ['recurringTemplates','recurringOccurrences','recurringOperations']) {
                await assertFails(getDocs(collection(other, `users/alice/${name}`)));
                await assertFails(getDoc(ref(other, name, name === 'recurringTemplates' ? 'rent' : 'anything')));
            }
            await assertFails(batch(other, createWrites(other, 'foreign')));
            await assertFails(batch(other, confirmWrites(other)));
            await assertFails(deleteDoc(template(other)));
        }
        await assertSucceeds(getDoc(template(d))); await assertSucceeds(getDocs(collection(d, 'users/alice/recurringTemplates')));
    });
});
