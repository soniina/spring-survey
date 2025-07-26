package learn.spring.survey.model

import jakarta.persistence.*

@Entity
@Table(name = "answers")
data class Answer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val text: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    val question: Question? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val respondent: User? = null,

    @OneToMany(mappedBy = "answer", cascade = [CascadeType.ALL], orphanRemoval = true)
    val selectedOptions: MutableList<SelectedOption> = mutableListOf()
)