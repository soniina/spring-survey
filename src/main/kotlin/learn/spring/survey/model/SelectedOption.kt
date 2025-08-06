package learn.spring.survey.model

import jakarta.persistence.*

@Entity
@Table(name = "selected_options")
data class SelectedOption(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id")
    val answer: Answer? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    val option: AnswerOption? = null
)
