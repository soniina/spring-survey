package learn.spring.survey.model

import jakarta.persistence.*

enum class QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TEXT
}

@Entity
@Table(name = "questions")
data class Question(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val text: String = "",

    val type: QuestionType = QuestionType.TEXT,

    @OneToMany(mappedBy = "question", cascade = [CascadeType.ALL], orphanRemoval = true)
    val options: MutableList<AnswerOption> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id")
    val survey: Survey? = null
)
