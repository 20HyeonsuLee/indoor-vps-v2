package kr.ac.koreatech.indoor.vps.contexts.mapping.application.slam;

/**
 * SLAM 측위 추상화. 기본 SuperPoint 파이프라인(primary)과 검증용 rtabmap-native
 * 파이프라인(shadow)이 같은 인터페이스를 통해 호출돼, Facade가 응답 path와
 * shadow logging path를 분기할 수 있게 함.
 */
public interface SlamLocalizer {
    SLAMLocalizeResult localize(LocalizeCommand command);
}
