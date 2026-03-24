package com.juno.advisor;

import com.juno.service.ProfileScoreService;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;

/**
 * Warns if profile completion is below threshold.
 * Appends a system message so the agent can proactively suggest profile improvements.
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
            var messages = new ArrayList<Message>(request.prompt().getInstructions());
            messages.add(new SystemMessage(
                    "PROFILE WARNING: The user's profile completion is " + score + "% "
                            + "(below " + threshold + "% threshold). "
                            + "Consider suggesting profile improvements for better job matches."
            ));

            request = request.mutate()
                    .prompt(new Prompt(messages, request.prompt().getOptions()))
                    .build();
        }

        return chain.nextCall(request);
    }
}
