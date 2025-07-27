package learn.spring.survey.model

import jakarta.persistence.*

enum class SurveyType {
    STANDARD,
    QUIZ,
    SCORED
}

@Entity
@Table(name = "surveys")
data class Survey (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val title: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val author: User? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var type: SurveyType = SurveyType.STANDARD,

    @OneToMany(mappedBy = "survey", cascade = [CascadeType.ALL], orphanRemoval = true)
    val questions: MutableList<Question> = mutableListOf(),
)
