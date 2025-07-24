package learn.spring.survey.model

import jakarta.persistence.*

@Entity
@Table(name = "questions")
data class Question(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val text: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id")
    val survey: Survey? = null
)
