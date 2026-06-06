package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionResponse;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedReaction;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemReaction;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class StudyRoomReactionService {

    public List<ReactionResponse> summarizeFeedReactions(Collection<StudyRoomFeedReaction> reactions, Long userId) {
        return summarize(reactions, userId, StudyRoomFeedReaction::getEmoji, reaction -> reaction.getUser().getId());
    }

    public List<ReactionResponse> summarizeSharedProblemReactions(Collection<StudyRoomSharedProblemReaction> reactions, Long userId) {
        return summarize(reactions, userId, StudyRoomSharedProblemReaction::getEmoji, reaction -> reaction.getUser().getId());
    }

    private <T> List<ReactionResponse> summarize(Collection<T> reactions, Long userId,
                                                 Function<T, String> emojiGetter,
                                                 Function<T, Long> userIdGetter) {
        Map<String, MutableReaction> grouped = new LinkedHashMap<>();
        for (T reaction : reactions) {
            String emoji = emojiGetter.apply(reaction);
            MutableReaction mutable = grouped.computeIfAbsent(emoji, key -> new MutableReaction());
            mutable.count++;
            if (userId.equals(userIdGetter.apply(reaction))) {
                mutable.reactedByMe = true;
            }
        }
        return grouped.entrySet().stream()
                .map(entry -> new ReactionResponse(entry.getKey(), entry.getValue().count, entry.getValue().reactedByMe))
                .toList();
    }

    private static class MutableReaction {
        private long count;
        private boolean reactedByMe;
    }
}
