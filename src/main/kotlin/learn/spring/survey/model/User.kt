package learn.spring.survey.model

import jakarta.persistence.*

enum class Role {
    USER,
    ADMIN
}

@Entity
@Table(name = "users")
class User (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val username: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val password: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val role: Role = Role.USER
) {
    constructor() : this(0, "", "", "", Role.USER)
    constructor(username: String, email: String, password: String) : this(0, username, email, password)
}
