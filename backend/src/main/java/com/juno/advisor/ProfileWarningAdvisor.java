package com.juno.advisor;

import com.juno.service.ProfileScoreService;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;

/**
 * Warns if profile completion is below threshold.
 * Ported from Juno agents/shared/middleware.py (profile_warning_middleware).
 *
 * Appends a system message warning when the user's profile is incomplete,
 * so the agent can proactively suggest profile improvements.
 */
public class ProfileWarningAdvisor implements CallAdvisor {

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
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        int score = scoreService.computeCurrentScore();

        if (score < threshold) {
            var messages = new ArrayList<>(request.prompt().getInstructions());
            messages.add(new SystemMessage(
                    "PROFILE WARNING: The user's profile completion is " + score + "% "
                            + "(below " + threshold + "% threshold). "
                            + "Consider suggesting profile improvements for better job matches."
            ));

            request = ChatClientRequest.builder()
                    .prompt(new Prompt(messages, request.prompt().getOptions()))
                    .context(request.context())
                    .build();
        }

        return chain.nextCall(request);
    }
}
