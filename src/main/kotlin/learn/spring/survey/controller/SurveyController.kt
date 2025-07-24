package learn.spring.survey.controller

import jakarta.validation.Valid
import learn.spring.survey.dto.*
import learn.spring.survey.security.UserPrincipal
import learn.spring.survey.service.SurveyService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/surveys")
class SurveyController (private val surveyService: SurveyService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSurvey(@RequestBody @Valid request: SurveyRequest, @AuthenticationPrincipal principal: UserPrincipal): SurveyResponse {
        return surveyService.createSurvey(request, principal.getUser())
    }

    @GetMapping("/{surveyId}")
    fun getSurveyQuestions(@PathVariable surveyId: Long): List<QuestionResponse> {
        return surveyService.getSurveyQuestions(surveyId)
    }

    @PostMapping("/{surveyId}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitAnswers(@PathVariable surveyId: Long, @RequestBody @Valid request: AnswerRequest,
                      @AuthenticationPrincipal principal: UserPrincipal): List<AnswerResponse> {
        return surveyService.submitAnswers(surveyId, request.answers, principal.getUser())
    }
}
