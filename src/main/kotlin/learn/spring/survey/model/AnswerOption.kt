package learn.spring.survey.model

import jakarta.persistence.*

@Entity
@Table(name = "answer_options")
data class AnswerOption(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val text: String = "",

    val isCorrect: Boolean = false,

    val points: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    val question: Question? = null
)
