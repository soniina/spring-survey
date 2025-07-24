package learn.spring.survey.repository

import learn.spring.survey.model.Answer
import org.springframework.data.jpa.repository.JpaRepository

interface AnswerRepository: JpaRepository<Answer, Long> {
    fun existsByRespondentIdAndQuestionSurveyId(respondentId: Long, questionId: Long): Boolean
}