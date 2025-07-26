package learn.spring.survey.repository

import learn.spring.survey.model.AnswerOption
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AnswerOptionRepository : JpaRepository<AnswerOption, Long>