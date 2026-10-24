const { assertFails, assertSucceeds, initializeTestEnvironment } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp } = require('firebase/firestore');
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
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const aliceContext = testEnv.authenticatedContext('alice');
        const aliceDb = aliceContext.firestore();
        await assertSucceeds(getDoc(doc(aliceDb, 'users', 'alice', 'expenses', 'e1')));

        const bobContext = testEnv.authenticatedContext('bob');
        const bobDb = bobContext.firestore();
        await assertFails(getDoc(doc(bobDb, 'users', 'alice', 'expenses', 'e1')));
    });

    it('should allow owner to update valid expense fields with a new updatedAt timestamp', async () => {
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

    it('should allow owner to delete their expense', async () => {
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

        await assertSucceeds(deleteDoc(ref));
    });

    it('should deny cross-user deletions', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const bobContext = testEnv.authenticatedContext('bob');
        const db = bobContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(deleteDoc(ref));
    });

    it('should deny unauthenticated deletions', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const unauth = testEnv.unauthenticatedContext();
        const db = unauth.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(deleteDoc(ref));
    });

    // INCOME RULES TESTS
    const mockValidLkrIncome = {
        sourceId: 'salary',
        receiptDate: '2023-10-23',
        currency: 'LKR',
        originalAmountStr: '128000.00',
        lkrAmountMinor: 12800000,
        conversionBasis: 'native_lkr',
        conversionRateStr: '1',
        note: ''
    };

    const mockValidUsdRateIncome = {
        sourceId: 'freelance',
        receiptDate: '2023-10-23',
        currency: 'USD',
        originalAmountStr: '41.50',
        lkrAmountMinor: 1245000,
        conversionBasis: 'confirmed_rate',
        conversionRateStr: '300',
        note: ''
    };

    const mockValidUsdCreditIncome = {
        sourceId: 'adsense',
        receiptDate: '2023-10-23',
        currency: 'USD',
        originalAmountStr: '41.50',
        lkrAmountMinor: 1245000,
        conversionBasis: 'actual_credit',
        note: 'Bank transfer'
    };

    it('should allow user to create LKR income', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertSucceeds(setDoc(ref, {
            ...mockValidLkrIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow user to create USD income with confirmed_rate', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i2');
        await assertSucceeds(setDoc(ref, {
            ...mockValidUsdRateIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow user to create USD income with actual_credit (no rate)', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i3');
        await assertSucceeds(setDoc(ref, {
            ...mockValidUsdCreditIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny cross-user income creation', async () => {
        const bob = testEnv.authenticatedContext('bob');
        const db = bob.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny unauthenticated income creation', async () => {
        const unauth = testEnv.unauthenticatedContext();
        const db = unauth.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow owner to read, update, delete income', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'incomeReceipts', 'i1'), {
                ...mockValidLkrIncome,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertSucceeds(getDoc(ref));
        await assertSucceeds(updateDoc(ref, {
            originalAmountStr: '130000',
            lkrAmountMinor: 13000000,
            updatedAt: serverTimestamp()
        }));
        await assertSucceeds(deleteDoc(ref));
    });

    it('should deny income updates that change createdAt', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'incomeReceipts', 'i1'), {
                ...mockValidLkrIncome,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertFails(updateDoc(ref, {
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny income creation with unexpected fields', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            maliciousField: true,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny income creation with zero original amount', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            originalAmountStr: '0.00',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));

        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            originalAmountStr: '000',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny income creation with oversized string', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            originalAmountStr: '123456789012345678901', // 21 chars
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny confirmed_rate income without conversionRateStr', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        const badUsd = { ...mockValidUsdRateIncome };
        delete badUsd.conversionRateStr;

        await assertFails(setDoc(ref, {
            ...badUsd,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny confirmed_rate income with zero or null rate', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertFails(setDoc(ref, {
            ...mockValidUsdRateIncome,
            conversionRateStr: '0.000',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));

        await assertFails(setDoc(ref, {
            ...mockValidUsdRateIncome,
            conversionRateStr: null,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny incompatible conversion basis (LKR with actual_credit)', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            conversionBasis: 'actual_credit',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });
});
