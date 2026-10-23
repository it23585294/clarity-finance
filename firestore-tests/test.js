const { assertFails, assertSucceeds, initializeTestEnvironment } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, serverTimestamp } = require('firebase/firestore');
const fs = require('fs');

describe('Clarity Expense Firestore Rules', function() {
    this.timeout(30000); // 30 seconds

    let testEnv;

    before(async () => {
        testEnv = await initializeTestEnvironment({
            projectId: 'demo-clarity-test',
            firestore: {
                rules: fs.readFileSync('../firestore.rules', 'utf8'),
                host: '127.0.0.1',
                port: 8080
            },
        });
    });

    after(async () => {
        if (testEnv) {
            await testEnv.cleanup();
        }
    });

    beforeEach(async () => {
        if (testEnv) {
            await testEnv.clearFirestore();
        }
    });

    const mockValidExpense = {
        amountMinor: 1500,
        currency: 'LKR',
        categoryId: 'food_dining',
        paymentChannelId: 'cash',
        spendingType: 'discretionary',
        expenseDate: '2023-10-23',
        note: ''
    };

    it('should allow user to create their own expense with valid data', async () => {
        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertSucceeds(setDoc(ref, {
            ...mockValidExpense,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny unauthenticated access', async () => {
        const unauth = testEnv.unauthenticatedContext();
        const db = unauth.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(getDoc(ref));
    });

    it('should deny cross-user writes', async () => {
        const bobContext = testEnv.authenticatedContext('bob');
        const db = bobContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(setDoc(ref, {
            ...mockValidExpense,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny invalid amounts (negative)', async () => {
        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(setDoc(ref, {
            ...mockValidExpense,
            amountMinor: -500,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny invalid category', async () => {
        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(setDoc(ref, {
            ...mockValidExpense,
            categoryId: 'invalid_cat',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow an owner to read their expense, but deny another user', async () => {
        // Seed valid expense for alice
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        // Alice can read her own expense
        const aliceContext = testEnv.authenticatedContext('alice');
        const aliceDb = aliceContext.firestore();
        await assertSucceeds(getDoc(doc(aliceDb, 'users', 'alice', 'expenses', 'e1')));

        // Bob cannot read Alice's expense
        const bobContext = testEnv.authenticatedContext('bob');
        const bobDb = bobContext.firestore();
        await assertFails(getDoc(doc(bobDb, 'users', 'alice', 'expenses', 'e1')));
    });

    it('should allow owner to update valid expense fields with a new updatedAt timestamp', async () => {
        // Seed valid expense for alice
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertSucceeds(updateDoc(ref, {
            amountMinor: 2000,
            note: 'Updated note',
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny updates that attempt to change createdAt', async () => {
        // Seed valid expense for alice
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(updateDoc(ref, {
            amountMinor: 2000,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });
});
