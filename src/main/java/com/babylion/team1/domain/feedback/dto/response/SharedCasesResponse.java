package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Example;
import com.babylion.team1.domain.feedback.repository.SharedCaseRepository.PublicCaseRow;
import java.util.List;
import org.springframework.data.domain.Page;

public record SharedCasesResponse(List<Item> items, int page, int size, long total) {

    public static SharedCasesResponse from(Page<PublicCaseRow> cases) {
        return new SharedCasesResponse(cases.stream().map(Item::from).toList(),
                cases.getNumber() + 1, cases.getSize(), cases.getTotalElements());
    }

    public record Item(
            Long sharedCaseId,
            int shareVersion,
            Long taskTypeId,
            String type,
            String title,
            String problem,
            String improvement,
            Example example,
            boolean isGuide
    ) {

        private static Item from(PublicCaseRow row) {
            var content = row.getContent();
            return new Item(row.getSharedCaseId(), row.getShareVersion(), row.getTaskTypeId(),
                    content.type(), content.title(), content.problem(), content.improvement(), content.example(),
                    row.getIsGuide());
        }
    }
}
