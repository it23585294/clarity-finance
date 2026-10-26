// Demo-emulator fixtures only. All payload shapes mirror FirestoreGoalRemote.
const { assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, getDoc, getDocs, collection, query, orderBy, setDoc, updateDoc, deleteDoc, serverTimestamp, writeBatch, runTransaction } = require('firebase/firestore');
const assert = require('assert');
module.exports = (environment) => describe('Savings allocation integrity', function () {
    const now = () => new Date(Date.now() + 330 * 60000).toISOString().slice(0, 10);
    const config = () => ({ name: 'Savings fixture', targetMinor: 49000000, openingMinor: 1120000, startDate: now(), deadline: `${Number(now().slice(0, 4)) + 1}${now().slice(4)}` });
    const db = (uid = 'alice') => environment().authenticatedContext(uid).firestore();
    const goal = (d, uid = 'alice') => doc(d, 'users', uid, 'savingsGoals', 'active');
    const receipt = (d, id, uid = 'alice') => doc(d, 'users', uid, 'savingsGoals', 'active', 'operations', id);
    const entry = (d, id = 'entry', uid = 'alice') => doc(d, 'users', uid, 'savingsGoals', 'active', 'entries', id);
    const ev = (e) => ({ kind: e.kind, amountMinor: e.amountMinor, entryDate: e.entryDate, note: e.note, deleted: e.deleted });
    const gv = (g) => Object.fromEntries(['name', 'targetMinor', 'openingMinor', 'startDate', 'deadline'].map(k => [k, g[k]]));
    function create(d, changes = {}, extras = () => {}) {
        const g = { ...config(), ...changes };
        const parent = { ...g, savedMinor: g.openingMinor, version: 0, lastOperationId: 'create', lastEntryId: '', createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
        const b = writeBatch(d); b.set(goal(d), parent);
        b.set(receipt(d, 'create'), { kind: 'CREATE', entryId: '', version: 0, payload: gv(g), createdAt: serverTimestamp() });
        extras(b, parent); return b.commit();
    }
    async function ledger(d, id, changes, kind = 'ENTRY_CREATE', overrides = {}, extra = () => {}) {
        const before = (await getDoc(goal(d))).data();
        const prior = await getDoc(entry(d, id));
        const old = prior.exists() ? prior.data() : null;
        const version = before.version + 1, op = `op-${version}`;
        const e = { kind: 'contribution', amountMinor: 1000000, entryDate: now(), note: '', deleted: false, ...old, ...changes,
            version, lastOperationId: op, updatedAt: serverTimestamp() };
        if (!old) e.createdAt = serverTimestamp();
        const effect = value => !value || value.deleted ? 0 : value.kind === 'contribution' ? value.amountMinor : -value.amountMinor;
        const b = writeBatch(d);
        b.update(goal(d), { savedMinor: before.savedMinor - effect(old) + effect(e), version, lastOperationId: op, lastEntryId: id, updatedAt: serverTimestamp(), ...overrides });
        b.set(entry(d, id), e);
        b.set(receipt(d, op), { kind, entryId: id, version, payload: ev(e), createdAt: serverTimestamp() });
        extra(b, e); return b.commit();
    }
    async function edit(d, changes = {}, overrides = {}) {
        const before = (await getDoc(goal(d))).data();
        const c = { ...gv(before), ...changes }, version = before.version + 1, op = `edit-${version}`;
        const b = writeBatch(d);
        b.update(goal(d), { ...c, savedMinor: before.savedMinor - before.openingMinor + c.openingMinor, version, lastOperationId: op,
            lastEntryId: '', updatedAt: serverTimestamp(), ...overrides });
        b.set(receipt(d, op), { kind: 'EDIT', entryId: '', version, payload: gv(c), createdAt: serverTimestamp() });
        return b.commit();
    }
    it('AT07 AT08 owner creation allocation corrections deletion and edits preserve separate finances', async () => {
        const d = db(); await assertSucceeds(create(d));
        await assertSucceeds(ledger(d, 'c', {})); assert.equal((await getDoc(goal(d))).data().savedMinor, 2120000);
        await assertSucceeds(ledger(d, 'w', { kind: 'withdrawal', amountMinor: 120000 }));
        assert.equal((await getDoc(goal(d))).data().savedMinor, 2000000);
        await assertSucceeds(ledger(d, 'c', { amountMinor: 1100000, entryDate: '2024-02-29', note: 'Corrected' }, 'ENTRY_UPDATE'));
        await assertSucceeds(edit(d, { name: 'Edited fixture', targetMinor: 100, openingMinor: 1120001 }));
        await assertSucceeds(ledger(d, 'w', { deleted: true }, 'ENTRY_DELETE'));
        assert.equal((await getDoc(goal(d))).data().savedMinor, 2220001);
        assert.equal((await getDocs(query(collection(goal(d), 'entries'), orderBy('entryDate')))).size, 2);
        for (const name of ['expenses', 'incomeReceipts', 'cryptoResults', 'expectedPayments'])
            assert.equal((await getDocs(collection(d, 'users', 'alice', name))).size, 0);
    });
    it('rejects counter-only writes entry-only writes and receipt-only writes', async () => {
        const d = db(); await create(d);
        await assertFails(updateDoc(goal(d), { savedMinor: 2120000, updatedAt: serverTimestamp() }));
        await assertFails(setDoc(entry(d), { kind: 'contribution', amountMinor: 1, entryDate: now(), note: '', deleted: false, version: 1,
            lastOperationId: 'only', createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
        await assertFails(setDoc(receipt(d, 'only'), { kind: 'EDIT', entryId: '', version: 0, payload: config(), createdAt: serverTimestamp() }));
    });
    it('rejects parent plus child creation bypass and multiple entries sharing one delta', async () => {
        const d = db();
        await assertFails(create(d, {}, (b) => b.set(entry(d), { kind: 'contribution', amountMinor: 1, entryDate: now(), note: '', deleted: false,
            version: 1, lastOperationId: 'create', createdAt: serverTimestamp(), updatedAt: serverTimestamp() })));
        await create(d);
        await assertFails(ledger(d, 'c', {}, 'ENTRY_CREATE', {}, (b, e) => b.set(entry(d, 'extra'), e)));
        await assertFails(ledger(d, 'c', {}, 'ENTRY_CREATE', { savedMinor: 1 }));
        assert.equal((await getDoc(goal(d))).data().savedMinor, 1120000);
    });
    it('rejects negative withdrawal edit deletion and opening correction', async () => {
        const d = db(); await create(d, { openingMinor: 0 });
        await assertFails(ledger(d, 'w', { kind: 'withdrawal', amountMinor: 1 }));
        await ledger(d, 'c', { amountMinor: 100 }); await ledger(d, 'w', { kind: 'withdrawal', amountMinor: 99 });
        await assertFails(ledger(d, 'c', { amountMinor: 98 }, 'ENTRY_UPDATE'));
        await assertFails(ledger(d, 'c', { deleted: true }, 'ENTRY_DELETE'));
        await assertSucceeds(edit(d, { openingMinor: 10 }));
        await assertFails(ledger(d, 'w', { amountMinor: 111 }, 'ENTRY_UPDATE'));
        assert.equal((await getDoc(goal(d))).data().savedMinor, 11);
    });
    it('rejects receipt replay duplicate increments tombstone resurrection and hard deletion', async () => {
        const d = db(); await create(d); await ledger(d, 'c', {});
        const accepted = (await getDoc(receipt(d, 'op-1'))).data();
        await assertFails(setDoc(receipt(d, 'op-1'), { ...accepted, createdAt: serverTimestamp() }));
        await assertFails(updateDoc(goal(d), { savedMinor: 3120000, version: 2, updatedAt: serverTimestamp() }));
        await ledger(d, 'c', { deleted: true }, 'ENTRY_DELETE');
        await assertFails(ledger(d, 'c', { deleted: false }, 'ENTRY_UPDATE'));
        await assertFails(deleteDoc(entry(d, 'c'))); await assertFails(deleteDoc(goal(d))); await assertFails(deleteDoc(receipt(d, 'op-1')));
    });
    it('enforces one active identity owner isolation and unauthenticated denial on every collection', async () => {
        const d = db(); await create(d);
        await assertFails(setDoc(doc(d, 'users/alice/savingsGoals/second'), (await getDoc(goal(d))).data()));
        for (const other of [db('bob'), environment().unauthenticatedContext().firestore()]) {
            for (const ref of [goal(other), entry(other), receipt(other, 'create')]) {
                await assertFails(getDoc(ref)); await assertFails(setDoc(ref, { invalid: true })); await assertFails(deleteDoc(ref));
            }
            await assertFails(getDocs(collection(goal(other), 'entries')));
        }
        await assertSucceeds(getDoc(goal(d))); await assertSucceeds(getDoc(receipt(d, 'create')));
    });
    it('strict goal schema types calendar dates future deadline and immutable start timestamps', async () => {
        const d = db();
        for (const bad of [{ targetMinor: 0 }, { targetMinor: 1.5 }, { openingMinor: -1 }, { name: '' }, { name: '  ' },
            { name: 'x'.repeat(101) }, { extra: true }, { startDate: '2025-02-29' }, { startDate: '1900-02-29' },
            { startDate: '2026-04-31' }, { deadline: now() }, { deadline: '9999-02-30' }, { startDate: '9999-01-01' }]) await assertFails(create(d, bad));
        await create(d, { startDate: '2024-02-29' });
        await assertFails(edit(d, { startDate: '2024-03-01' }));
        await assertFails(edit(d, {}, { createdAt: serverTimestamp() }));
        await assertFails(edit(d, {}, { version: 5 }));
    });
    it('strict ledger amount calendar note direction timestamps and exact deletion payload', async () => {
        const d = db(); await create(d);
        const tomorrow = new Date(Date.now() + 330 * 60000 + 86400000).toISOString().slice(0, 10);
        for (const bad of [{ amountMinor: 0 }, { amountMinor: -1 }, { amountMinor: 1.5 }, { kind: 'income' }, { note: 1 },
            { note: 'x'.repeat(501) }, { entryDate: '2025-02-29' }, { entryDate: '0000-01-01' }, { entryDate: tomorrow }, { extra: 1 }])
            await assertFails(ledger(d, 'c', bad));
        await ledger(d, 'c', { entryDate: '2000-02-29' });
        await assertFails(ledger(d, 'c', { createdAt: serverTimestamp() }, 'ENTRY_UPDATE'));
        await assertFails(ledger(d, 'c', { deleted: true, note: 'Changed while deleting' }, 'ENTRY_DELETE'));
    });
    it('naturally overdue goal remains inspectable editable and deadline can be revised', async () => {
        const d = db();
        await environment().withSecurityRulesDisabled(async context => {
            await setDoc(goal(context.firestore()), { ...config(), startDate: '2024-01-31', deadline: '2024-12-31', savedMinor: 1120000,
                version: 0, lastOperationId: 'seed', lastEntryId: '', createdAt: new Date(0), updatedAt: new Date(0) });
        });
        await assertSucceeds(getDoc(goal(d))); await assertSucceeds(edit(d, { name: 'Still editable' }));
        await assertFails(edit(d, { deadline: '2025-01-01' }));
        await assertSucceeds(edit(d, { deadline: config().deadline }));
    });
    it('concurrent withdrawals use version preconditions and exactly one can consume the balance', async () => {
        const d = db(); await create(d, { openingMinor: 100 });
        const withdraw = id => runTransaction(d, async tx => {
            const g = (await tx.get(goal(d))).data();
            assert.equal(g.version, 0, 'Stale-version conflict');
            const e = { kind: 'withdrawal', amountMinor: 80, entryDate: now(), note: '', deleted: false, version: 1,
                lastOperationId: id, createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
            tx.update(goal(d), { savedMinor: g.savedMinor - 80, version: 1, lastOperationId: id, lastEntryId: id, updatedAt: serverTimestamp() });
            tx.set(entry(d, id), e); tx.set(receipt(d, id), { kind: 'ENTRY_CREATE', entryId: id, version: 1, payload: ev(e), createdAt: serverTimestamp() });
        });
        const results = await Promise.allSettled([withdraw('one'), withdraw('two')]);
        assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
        assert.equal((await getDoc(goal(d))).data().savedMinor, 20);
        assert.equal((await getDocs(collection(goal(d), 'entries'))).size, 1);
    });
    it('rejects a fully valid cross-user atomic allocation, not just malformed foreign documents', async () => {
        const d = db(); await create(d);
        for (const other of [db('bob'), environment().unauthenticatedContext().firestore()]) {
            const e = { kind: 'contribution', amountMinor: 100, entryDate: now(), note: '', deleted: false, version: 1,
                lastOperationId: 'foreign', createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
            const b = writeBatch(other);
            b.update(goal(other), { savedMinor: 1120100, version: 1, lastOperationId: 'foreign', lastEntryId: 'foreign', updatedAt: serverTimestamp() });
            b.set(entry(other, 'foreign'), e);
            b.set(receipt(other, 'foreign'), { kind: 'ENTRY_CREATE', entryId: 'foreign', version: 1, payload: ev(e), createdAt: serverTimestamp() });
            await assertFails(b.commit());
        }
        assert.equal((await getDoc(goal(d))).data().savedMinor, 1120000);
    });
    it('rejects signed delta wraparound using exact 64-bit REST integers', async () => {
        // JS Number cannot represent Long.MAX_VALUE. REST integerValue strings keep this adversarial fixture exact.
        const project = 'demo-clarity-test';
        const base = `projects/${project}/databases/(default)/documents/users/alice/savingsGoals/active`;
        const token = `${Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')}.${Buffer.from(JSON.stringify({
            sub: 'alice', user_id: 'alice', aud: project, iss: `https://securetoken.google.com/${project}`,
            iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600, firebase: { sign_in_provider: 'custom' }
        })).toString('base64url')}.`;
        const encode = value => typeof value === 'bigint' ? { integerValue: value.toString() }
            : typeof value === 'number' ? { integerValue: String(value) }
            : typeof value === 'boolean' ? { booleanValue: value }
            : typeof value === 'string' ? { stringValue: value }
            : { mapValue: { fields: Object.fromEntries(Object.entries(value).map(([k, v]) => [k, encode(v)])) } };
        let created;
        const MAX = 9223372036854775807n;
        const c = { ...config(), openingMinor: MAX, targetMinor: MAX };
        async function commit(version, id, saved, e, kind) {
            const parent = { ...c, savedMinor: saved, version, lastOperationId: id, lastEntryId: e ? 'w' : '' };
            const fields = Object.fromEntries(Object.entries(parent).map(([k, v]) => [k, encode(v)]));
            if (created) fields.createdAt = created;
            const writes = [{ update: { name: base, fields }, updateTransforms: [
                ...(!created ? [{ fieldPath: 'createdAt', setToServerValue: 'REQUEST_TIME' }] : []),
                { fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' }] }];
            if (e) {
                const entryFields = encode({ ...e, version, lastOperationId: id }).mapValue.fields;
                if (e.createdAt) { delete entryFields.createdAt; entryFields.createdAt = e.createdAt; }
                writes.push({ update: { name: `${base}/entries/w`, fields: entryFields }, updateTransforms: [
                    ...(!e.createdAt ? [{ fieldPath: 'createdAt', setToServerValue: 'REQUEST_TIME' }] : []),
                    { fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' }] });
            }
            const payload = e ? ev(e) : c;
            writes.push({ update: { name: `${base}/operations/${id}`, fields: encode({ kind, entryId: e ? 'w' : '', version, payload }).mapValue.fields },
                updateTransforms: [{ fieldPath: 'createdAt', setToServerValue: 'REQUEST_TIME' }] });
            return fetch(`http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents:commit`, {
                method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ writes })
            });
        }
        let response = await commit(0, 'max-create', MAX, null, 'CREATE'); assert.equal(response.status, 200, await response.text());
        const read = async path => (await fetch(`http://127.0.0.1:8080/v1/${path}`, { headers: { Authorization: `Bearer ${token}` } })).json();
        created = (await read(base)).fields.createdAt;
        const w = { kind: 'withdrawal', amountMinor: MAX, entryDate: now(), note: '', deleted: false };
        response = await commit(1, 'max-withdraw', 0n, w, 'ENTRY_CREATE'); assert.equal(response.status, 200, await response.text());
        // Increase opening by zero is not enough: a separate valid contribution supplies the balance that a wrapped delta could erase.
        await ledger(db(), 'tiny', { amountMinor: 2 });
        const entryCreated = (await read(`${base}/entries/w`)).fields.createdAt;
        response = await commit(3, 'overflow-flip', 0n, { ...w, kind: 'contribution', createdAt: entryCreated }, 'ENTRY_UPDATE');
        assert.equal(response.status, 403, await response.text());
        assert.equal((await read(base)).fields.savedMinor.integerValue, '2');
    });
});
