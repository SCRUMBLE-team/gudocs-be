package com.scrumble.gudocs.subscriptions.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionCategory {
    OTT("영상 스트리밍"),
    MUSIC("음악"),
    CLOUD("클라우드"),
    PRODUCTIVITY("생산성"),
    AI("AI"),
    NEWS("뉴스"),
    EDUCATION("교육"),
    GAME("게임"),
    SHOPPING("쇼핑"),
    DESIGN("디자인"),
    ETC("기타");

    private final String displayName;
}
