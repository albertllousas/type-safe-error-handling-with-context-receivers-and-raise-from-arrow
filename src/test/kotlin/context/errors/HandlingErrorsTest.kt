package context.errors

import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class HandlingErrorsTest {

    private val userRepository = mockk<UserRepository>(relaxUnitFun = true)

    private val handLingErrors = HandlingErrors(userRepository)

    @Nested
    inner class PropagatingASingleError {

        @Test
        fun `should find user`() {
            val user = User(UUID.randomUUID(), "John", emptyList())
            val result = either {
                every { userRepository.find(user.id) } returns user

                handLingErrors.findUser(user.id)
            }
            result shouldBe user.right()
        }

        @Test
        fun `should fail finding user`() {
            val result = either {
                every { userRepository.find(any()) } answers { raise(UserNotFound) }

                handLingErrors.findUser(UUID.randomUUID())
            }
            result shouldBe UserNotFound.left()
        }
    }

    @Nested
    inner class PropagateMultipleErrors {

        @Test
        fun `should follow a user`() {
            val follower = User(UUID.randomUUID(), "John", emptyList())
            val followed = User(UUID.randomUUID(), "Jane", emptyList())
            val result = either {
                every { userRepository.find(follower.id) } returns follower
                every { userRepository.find(followed.id) } returns followed

                handLingErrors.follow(follower.id, followed.id)
            }
            result shouldBe follower.copy(following = listOf(followed)).right()
        }

        @Test
        fun `should fail following a user if any of the involved users does not exists`() {
            val follower = User(UUID.randomUUID(), "John", emptyList())
            val followed = User(UUID.randomUUID(), "Jane", emptyList())
            val result = either {
                every { userRepository.find(follower.id) } returns follower
                every { userRepository.find(followed.id) } answers { raise(UserNotFound) }

                handLingErrors.follow(follower.id, followed.id)
            }
            result shouldBe UserNotFound.left()
        }

        @Test
        fun `should fail following a user if it was already followed`() {
            val followed = User(UUID.randomUUID(), "Jane", emptyList())
            val follower = User(UUID.randomUUID(), "John", listOf(followed))
            val result = either {
                every { userRepository.find(follower.id) } returns follower
                every { userRepository.find(followed.id) } returns followed

                handLingErrors.follow(follower.id, followed.id)
            }
            result shouldBe UserAlreadyFollowed.left()
        }
    }

    @Nested
    inner class RecoverFromASingleError {

        @Test
        fun `should say hello to a user`() {
            val user = User(UUID.randomUUID(), "John", emptyList())
            every {
                with(any<Raise<Any>>()) { userRepository.find(user.id) }
            } returns user
            val result = handLingErrors.sayHelloTo(user.id)
            result shouldBe "Hi John!"
        }

        @Test
        fun `should recover from saying hello when user does not exist`() {
            val handLingErrors = HandlingErrors(object : UserRepository {
                context(Raise<UserNotFound>) override fun find(id: UUID): User = raise(UserNotFound)
                override fun save(user: User): Unit = TODO("Not yet implemented")
            })

            val result = handLingErrors.sayHelloTo(UUID.randomUUID())

            result shouldBe "Sorry, user does not exists"
        }
    }

    @Nested
    inner class RecoverFromMultipleErrors {

        @Test
        fun `should follow a user and inform of the success`() {
            val follower = User(UUID.randomUUID(), "John", emptyList())
            val followed = User(UUID.randomUUID(), "Jane", emptyList())
            every { with(any<Raise<Any>>()) { userRepository.find(follower.id) } } returns follower
            every { with(any<Raise<Any>>()) { userRepository.find(followed.id) } } returns followed

            val result = handLingErrors.followWithFullErrorRecovering(follower.id, followed.id)

            result shouldBe "John follows Jane!"
        }

        @Test
        fun `should inform if any of the involved users does not exists`() {
            val follower = User(UUID.randomUUID(), "John", emptyList())
            val followed = User(UUID.randomUUID(), "Jane", emptyList())

            val result = HandlingErrors(object : UserRepository {
                context(Raise<UserNotFound>) override fun find(id: UUID): User = raise(UserNotFound)
                override fun save(user: User): Unit = TODO("Not yet implemented")
            }).followWithFullErrorRecovering(follower.id, followed.id)

            result shouldBe "Sorry, user does not exists"
        }

        @Test
        fun `should inform when a user when it was already followed`() {
            val followed = User(UUID.randomUUID(), "Jane", emptyList())
            val follower = User(UUID.randomUUID(), "John", listOf(followed))
            every { with(any<Raise<Any>>()) { userRepository.find(follower.id) } } returns follower
            every { with(any<Raise<Any>>()) { userRepository.find(followed.id) } } returns followed

            val result = handLingErrors.followWithFullErrorRecovering(follower.id, followed.id)

            result shouldBe "User already followed"
        }
    }

    @Nested
    inner class RecoverFromOneErrorAndPropagateUpTheRest {

        @Test
        fun `should follow a user and inform of the success`() {
            val follower = User(UUID.randomUUID(), "John", emptyList())
            val followed = User(UUID.randomUUID(), "Jane", emptyList())
            val result = either {
                every { with(any<Raise<Any>>()) {userRepository.find(follower.id) } } returns follower
                every { with(any<Raise<Any>>()) {userRepository.find(followed.id) } } returns followed

                handLingErrors.followWithPartialErrorRecovering(follower.id, followed.id)
            }
            result shouldBe "John follows Jane!".right()
        }

        @Test
        fun `should inform when a user when it was already followed`() {
            val followed = User(UUID.randomUUID(), "Jane", emptyList())
            val follower = User(UUID.randomUUID(), "John", listOf(followed))
            val result = either {
                every { with(any<Raise<Any>>()) {userRepository.find(follower.id) } } returns follower
                every { with(any<Raise<Any>>()) {userRepository.find(followed.id) } } returns followed

                handLingErrors.followWithPartialErrorRecovering(follower.id, followed.id)
            }

            result shouldBe "User already followed".right()
        }

        @Test
        fun `should fail if any of the involved users does not exists`() {
            val follower = User(UUID.randomUUID(), "John", emptyList())
            val followed = User(UUID.randomUUID(), "Jane", emptyList())

            val result = either {
                HandlingErrors(object : UserRepository {
                    context(Raise<UserNotFound>) override fun find(id: UUID): User = raise(UserNotFound)
                    override fun save(user: User): Unit = TODO("Not yet implemented")
                }).followWithFullErrorRecovering(follower.id, followed.id)
            }

            result shouldBe UserNotFound.left()
        }
    }
}

