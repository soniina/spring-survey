package learn.spring.survey.repository

import learn.spring.survey.model.Survey
import org.springframework.data.jpa.repository.JpaRepository

interface SurveyRepository: JpaRepository<Survey, Long> {
    fun existsByTitle(title: String): Boolean
}