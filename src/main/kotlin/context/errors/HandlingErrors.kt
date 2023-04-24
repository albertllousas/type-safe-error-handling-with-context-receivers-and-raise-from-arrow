package context.errors

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.recover
import java.util.UUID

sealed interface DomainError

object UserNotFound : DomainError

object UserAlreadyFollowed : DomainError

data class User(val id: UUID, val name: String, val following: List<User>) {

    context(Raise<UserAlreadyFollowed>)
    fun follow(user: User): User {
        ensure(condition = !this.following.contains(user)) { UserAlreadyFollowed }
        return this.copy(following = following + user)
    }
}

interface UserRepository {

    context(Raise<UserNotFound>)
    fun find(id: UUID): User

    fun save(user: User)
}

class HandlingErrors(private val userRepository: UserRepository) {

    /**
     * Use-case: Propagate a single error
     */
    context(Raise<UserNotFound>)
    fun findUser(id: UUID): User =
        userRepository.find(id)

    /**
     * Use-case: Propagate multiple errors
     */
    context(Raise<UserNotFound>, Raise<UserAlreadyFollowed>)
    fun follow(followerId: UUID, followedId: UUID): User {
        val follower = userRepository.find(followerId)
        val followed = userRepository.find(followedId)
        return follower.follow(followed)
    }

    /**
     * Use-case: Recover from a single error
     */
    fun sayHelloTo(userId: UUID): String =
        recover(
            block = { userRepository.find(userId).let { "Hi ${it.name}!" } },
            recover = { "Sorry, user does not exists" }
        )

    /**
     * Use-case: Recover from multiple errors
     */
    fun followWithFullErrorRecovering(followerId: UUID, followedId: UUID): String =
        recover(
            block = {
                val follower = userRepository.find(followerId)
                val followed = userRepository.find(followedId)
                val updatedUser = follower.follow(followed)
                userRepository.save(updatedUser)
                "${follower.name} follows ${followed.name}!"
            },
            recover = { error ->
                when (error) {
                    is UserAlreadyFollowed -> "User already followed"
                    is UserNotFound -> "Sorry, user does not exists"
                }
            }
        )

    /**
     * Use-case: Recover from one error and propagate up the rest
     */
    context(Raise<UserNotFound>)
    fun followWithPartialErrorRecovering(followerId: UUID, followedId: UUID): String =
        recover(
            block = {
                val follower = userRepository.find(followerId)
                val followed = userRepository.find(followedId)
                val updatedUser = follower.follow(followed)
                userRepository.save(updatedUser)
                "${follower.name} follows ${followed.name}!"
            },
            recover = { error ->
                when (error) {
                    is UserAlreadyFollowed -> "User already followed"
                    is UserNotFound -> raise(error)
                }
            }
        )
}
