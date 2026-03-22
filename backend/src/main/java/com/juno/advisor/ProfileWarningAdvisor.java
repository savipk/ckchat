package com.juno.advisor;

import com.juno.service.ProfileScoreService;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns if profile completion is below threshold.
 * Ported from Juno agents/shared/middleware.py (profile_warning_middleware).
 *
 * Appends a system message warning when the user's profile is incomplete,
 * so the agent can proactively suggest profile improvements.
 */
public class ProfileWarningAdvisor implements CallAroundAdvisor {

    private final ProfileScoreService scoreService;
    private final int threshold;

    public ProfileWarningAdvisor(ProfileScoreService scoreService, int threshold) {
        this.scoreService = scoreService;
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "ProfileWarningAdvisor";
    }

    @Override
    public int getOrder() {
        return 300;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        int score = scoreService.computeCurrentScore();

        if (score < threshold) {
            var messages = new ArrayList<Message>(request.messages());
            messages.add(new SystemMessage(
                    "PROFILE WARNING: The user's profile completion is " + score + "% "
                            + "(below " + threshold + "% threshold). "
                            + "Consider suggesting profile improvements for better job matches."
            ));

            request = AdvisedRequest.from(request)
                    .messages(messages)
                    .build();
        }

        return chain.nextAroundCall(request);
    }
}
