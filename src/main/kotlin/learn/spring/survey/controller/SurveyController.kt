package learn.spring.survey.controller

import jakarta.validation.Valid
import learn.spring.survey.dto.SurveyRequest
import learn.spring.survey.dto.SurveyResponse
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
}
