/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain

import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.AuthResult
import com.gy.githubclient.feature.auth.domain.model.AuthSession
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.model.AuthUser
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithPatUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 领域模型纯逻辑测试，不依赖 Android 框架。 */
class AuthDomainTest {
    private val stubUser =
        AuthUser(
            login = "gy",
            displayName = "Gy",
            avatarUrl = null,
            htmlUrl = "https://github.com/gy",
        )

    @Test
    fun signedInRequiresPrimaryTokenAndUser() {
        val withBoth =
            AuthSession(
                user = stubUser,
                hasPrimaryToken = true,
                hasNotificationToken = false,
                method = AuthMethod.PAT,
            )
        val withoutToken = withBoth.copy(hasPrimaryToken = false)
        val withoutUser = withBoth.copy(user = null)

        assertTrue(withBoth.isSignedIn)
        assertFalse(withoutToken.isSignedIn)
        assertFalse(withoutUser.isSignedIn)
    }

    @Test
    fun blankPrimaryTokenIsRejectedWithoutCallingRepository() =
        runTest {
            var called = false
            val repo =
                object : StubAuthRepository() {
                    override suspend fun signInWithPat(token: String): AuthResult {
                        called = true
                        return AuthResult.Success(
                            AuthSession(stubUser, true, false, AuthMethod.PAT),
                        )
                    }
                }
            val useCase = SignInWithPatUseCase(repo)

            val result = useCase("   ")

            assertTrue(result is AuthResult.Failure.InvalidToken)
            assertEquals(TokenChannel.PRIMARY, (result as AuthResult.Failure.InvalidToken).channel)
            assertFalse("空白 token 不应触发网络调用", called)
        }

    @Test
    fun validPatReturnsSuccess() =
        runTest {
            val session = AuthSession(stubUser, true, false, AuthMethod.PAT)
            val repo =
                object : StubAuthRepository() {
                    override suspend fun signInWithPat(token: String) = AuthResult.Success(session)
                }
            val result = SignInWithPatUseCase(repo)("ghp_valid")

            assertTrue(result is AuthResult.Success)
            assertEquals(AuthMethod.PAT, repo.lastMethod)
        }
}

/** 测试替身：只实现被用例触碰的方法，其余给默认空实现。 */
private open class StubAuthRepository : com.gy.githubclient.feature.auth.domain.repository.AuthRepository {
    var lastMethod: AuthMethod? = null
        private set

    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedOut)

    override suspend fun currentSession(): AuthSession =
        AuthSession(null, false, false, null)

    override suspend fun restoreSession(): AuthState = AuthState.SignedOut

    override suspend fun signInWithPat(token: String): AuthResult =
        AuthResult.Failure.Network("stub")

    override suspend fun signInWithOAuth(accessToken: String): AuthResult =
        AuthResult.Failure.Network("stub")

    override suspend fun bindNotificationToken(token: String): AuthResult =
        AuthResult.Failure.Network("stub")

    override suspend fun getPrimaryToken(): String? = null

    override suspend fun getNotificationToken(): String? = null

    override suspend fun signOut(channel: TokenChannel?) = Unit

    override suspend fun setAuthMethod(method: AuthMethod) {
        lastMethod = method
    }
}
