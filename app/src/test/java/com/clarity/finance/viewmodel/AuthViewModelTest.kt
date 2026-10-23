package com.clarity.finance.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var repository: FakeAuthRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAuthRepository()
        viewModel = AuthViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login with empty fields sets error`() = runTest {
        viewModel.login("", "")
        val state = viewModel.loginFormState.value
        assertEquals("Please enter both email and password.", state.error)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `login with invalid email sets error`() = runTest {
        viewModel.login("invalid-email", "password123")
        val state = viewModel.loginFormState.value
        assertEquals("Invalid email format.", state.error)
    }

    @Test
    fun `login with valid credentials successfully updates state`() = runTest {
        // Setup existing user
        repository.registeredUsers["test@example.com"] = "password123"

        viewModel.login("test@example.com", "password123")
        
        // Assert submitting state is true initially
        assertTrue(viewModel.loginFormState.value.isSubmitting)

        // Run coroutines
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert error is null, submitting is false
        val state = viewModel.loginFormState.value
        assertNull(state.error)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `login with incorrect password sets generic error`() = runTest {
        repository.registeredUsers["test@example.com"] = "password123"

        viewModel.login("test@example.com", "wrongpassword")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.loginFormState.value
        assertEquals("Email or password is incorrect.", state.error)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `register with mismatched passwords sets error`() = runTest {
        viewModel.register("test@example.com", "password123", "password321")
        val state = viewModel.registerFormState.value
        assertEquals("Passwords do not match.", state.error)
    }

    @Test
    fun `register with valid data successfully updates state`() = runTest {
        viewModel.register("test@example.com", "password123", "password123")
        
        assertTrue(viewModel.registerFormState.value.isSubmitting)
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.registerFormState.value
        assertNull(state.error)
        assertFalse(state.isSubmitting)
        
        // Ensure user is added to fake repository
        assertTrue(repository.registeredUsers.containsKey("test@example.com"))
    }
    
    @Test
    fun `prevent duplicate submissions`() = runTest {
        // Freeze dispatcher so the first login doesn't complete
        viewModel.login("test@example.com", "password123")
        assertTrue(viewModel.loginFormState.value.isSubmitting)
        
        // Clear error manually just to be sure we can check it
        viewModel.clearErrors()
        
        // Try to login again while submitting
        viewModel.login("", "") // Invalid login, should normally trigger error
        
        // Verify error is not set because submission was blocked
        assertNull(viewModel.loginFormState.value.error)
        
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
