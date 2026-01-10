# PRODUCT API
많은 사용자가 동시에 접속하는 상황에서도 빠르고 안정적으로 동작하는 API를 구현하고 싶다는 생각에서 프로젝트를 시작했습니다. 단순히 기능이 동작하는 수준에 그치지 않고, 실제 서비스 환경에서 구조나 설정이 성능에 어떤 영향을 주는지 직접 검증해보고자 했습니다.<br>
<br>
운영 환경과 유사한 인프라를 구성한 뒤, 부하 테스트 도구(nGrinder) 를 활용해 다양한 트래픽 조건을 시뮬레이션하며 TPS, 응답 시간, 오류율 등 주요 지표를 측정했습니다. 이 과정에서 캐시 구조, 락 방식, DB 커넥션 설정 등 시스템 병목을 유발하는 요인을 찾아내고, 이를 개선하기 위해 여러 아키텍처적 실험을 수행했습니다.<br>
<br>
이 프로젝트는 단순한 CRUD 서버가 아니라, “트래픽 증가에도 안정적으로 확장 가능한 백엔드 설계”를 기술적으로 해결하고자 한 시도였으며, 실제 운영 환경에서도 활용 가능한 설계 원리를 검증하는 데 목표를 두었습니다.<br>
<br>

# Architecture
<img width="798" height="469" alt="image" src="https://github.com/user-attachments/assets/df38bdf1-39bc-4c47-b29b-6de21e50224c" />

## 주요기능
- 상품 등록(Create)
- 상품 조회(Read)
- 상품 수정(Update)
- 상품 삭제(Delete)
<br>

## 기술스택
Java17, Spring Boot, JPA, MySQL, Docker, Ngnix, Redis, AWS, Kafka
<br>
<br>

## 분산 환경 성능 테스트 비교(상품 조회)
데이터 30,000개로 진행(DB maximum pool size = 10) -> OOM 발생(메모리 사용률 98%) -> 에러율 30% 이상
<br>

```
2025-06-10T09:17:22.059Z ERROR 1 --- [test-repo-java] [io-8080-exec-45] o.a.c.c.C.[Tomcat].[localhost]: Exception Processing [ErrorPage[errorCode=0, location=/error]]
jakarta.servlet.ServletException: Handler dispatch failed: java.lang.OutOfMemoryError: Java heap space at …
```
1. DB maximum pool size 10 -> 50 증가
2. Vusers를 최대 75로 제한(최대치 이상으로 하면 OOM 발생)

### Local(Vuser 증가 25 -> 50 -> 75)
Duration : 300(sec), Data : 30,000 **Vuser : 25**<br>
✔️Application Instance(1~3)   ✔️Ngnix   ✔️Ngrinder(controller, agent)   ✔️MySql


