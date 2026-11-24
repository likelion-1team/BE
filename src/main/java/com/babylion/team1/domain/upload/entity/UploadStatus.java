package com.babylion.team1.domain.upload.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UploadStatus {

	UPLOADED("업로드 완료"),
	ANALYZING("분석 중"),
	DONE("분석 완료"),
	FAILED("분석 실패"),
	CONSUMED("선택 확정");

	private final String description;
}
