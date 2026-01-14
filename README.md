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

<br>

### Local(Vuser 증가 25 -> 50 -> 75)
✔️Application Instance(1~3)   ✔️Ngnix   ✔️Ngrinder(controller, agent)   ✔️MySql <br>
<br>
Duration : 300(sec), Data : 30,000, **Vuser : 25**<br>
| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |비고 |
|---------|-----------|------------------|------------------|------------------|-----------|-----------|
| 단일 인스턴스 | 80.1 | 297.0 | 13.5 | 119.0 | 0% | |
| Nginx + 인스턴스 2개 | 33.1 | 718.7 | 6.0 | 50.0 | 0.01% | least_conn X
| Nginx + 인스턴스 3개 | 31.2 | 762.66 | 20.0 | 45.5 | 0% | least_conn O

<br>

### least_conn 추가 
Nginx + 인스턴스 2개 부하 테스트 - 에러율이 한 곳에서 치솟는 현상 발견
<img width="1000" height="244" alt="image" src="https://github.com/user-attachments/assets/48689f23-d603-432c-8486-3b056243dd2d" />

| 적용 전 (least_conn ❌) | 적용 후 (least_conn ⭕) |
|------------------------|------------------------|
|<img width="1548" height="842" alt="image" src="https://github.com/user-attachments/assets/6de4d110-ceec-4f08-bf7d-3ebe60ffcfd7" />|<img width="1555" height="823" alt="image" src="https://github.com/user-attachments/assets/ca17df36-a566-4d2e-b87b-6e1e558ebdfb" />|
|33:15부터 1.3초에 가까운 GC 발생 <br>-> 에러 발생시점과 일치하다 판단<br><br> product-api-1(초록색)과 product-api-2(노란색) 그래프 차이 존재<br> → 부하 쏠림 예측|추가 후 2개의 그래프 비슷해짐 <br>-> 이전보다 균등하게 분배되었다고 판단(y축 값도 작아짐)|
|<img width="1588" height="826" alt="image" src="https://github.com/user-attachments/assets/89dc9ff9-ff93-4e54-8b5e-b36c92bc6ec3" />|<img width="1611" height="833" alt="image" src="https://github.com/user-attachments/assets/6e27c519-e411-47d1-8847-be755ce1e7a2" />|
|주황색(product-api-2) 00:33 ~ 00:35 사이 Eden 공간이 급증<br> → GC 예측<br><br>파란색(product-api-1)에 비해 높은 것으로 보아 부하 쏠림 가능성 존재|파란색(product-api-1)의 변화폭 증가<br> → 부하가 이전보다 균등하게 배분된다고 판단|
<br>

Nginx + 인스턴스 2개 부하 테스트 <br>
| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|---------|-----------|------------------|------------------|------------------|-----------|
| least_conn 추가 전 | 33.1| 718.7| 6 | 50.0 | 0.01% |
| least_conn 추가 후 | 34.1 | 699.5 | 14.5 | 50.5 | 0% |

User의 수가 크지 않아 값의 변화는 작지만, 테스트를 3차례 추가 시도시 에러는 0%로 발생하지 않았음
<br>
<br>

Duration : 300(sec), Data : 30,000, **Vuser : 50**, least_conn 추가<br>
| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|---------|-----------|------------------|------------------|------------------|-----------|
| 단일 인스턴스 | 63.7 | 781.9 | 38.0 | 109.5 | 0% |
| Nginx + 인스턴스 2개 | 25.4 | 1954.2 | 8.5 | 38.5 | 0.07% |
| Nginx + 인스턴스 3개 | 25.8 | 1906.1 | 7.5 | 41.5 | 0.43% |

<br>

Duration : 300(sec), Data : 30,000, **Vuser : 75**, least_conn 추가<br>
| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|---------|-----------|------------------|------------------|------------------|-----------|
| 단일 인스턴스 | 68.9 | 1072.2 | 15.5 | 101.5 | 0% |
| Nginx + 인스턴스 2개 | 26.7 | 2661.1 | 3.5 | 38.0 | 1.67% |
| Nginx + 인스턴스 3개 | 23.0 | 3146.8 | 7.5 | 35.0 | 1.05% |

<br>

```
로컬 개발 환경에서 진행되어,
테스트 진행 중 웹 브라우저 사용, 음악 재생 등 다른 프로세스의 리소스 사용이 일부 지표에 영향을 미쳤을 가능성 존재

이러한 한계를 보완하기 위해,
동일한 테스트 시나리오를 AWS EC2 기반 독립 환경에서 다시 수행하여 외부 요인의 영향을 최소화한 상태에서 성능을 재검증
```

### AWS(EC2, Vuser 증가 25 -> 50 -> 75)
✔️t2.micro <br>
✔️Application Instance(1~3)   ✔️Ngnix   ✔️Ngrinder(controller, agent)   ✔️MySql
<br>
<br>Duration : 300(sec), Data : 30,000, **Vuser : 25**<br>
| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|---------|-----------|------------------|------------------|------------------|-----------|
| 단일 인스턴스 | 20.1 | 1158.82 | 2 | 29 | 0.40% |
| Nginx + 인스턴스 2개 | 45.0 | 527.21 | 19 | 57.5 | 0% |
| Nginx + 인스턴스 3개 | 64.9 | 366.19 | 37 | 85 | 0% |

<br>Duration : 300(sec), Data : 30,000, **Vuser : 50**<br>
| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|---------|------------|-------------------|-------------------|-------------------|-----------|
| 단일 인스턴스 | 8.1 | 0.23 | 0 | 25 | 48.47 |
| Nginx + 인스턴스 2개 | 23.7 | 105.26 | 0 | 33.5 | 24.71 |
| Nginx + 인스턴스 3개 | 41.0 | 130.50 | 31 | 76 | 15.14 |

<img width="254" height="233" alt="image" src="https://github.com/user-attachments/assets/47b610bc-4d1d-4aeb-b676-b09bfe22d5af" />
GC 일시정지 시간이 최대 1초가 나와 사용자 요청 처리가 지연되거나 응답 속도가 늦어져 에러율 증가로 예측

<br>

<br>Duration : 300(sec), Data : 30,000, **Vuser : 75**<br>
| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 최소 TPS (ms) | 최대 TPS (ms) | 에러율 (%) |
|---------|------------|-------------------|--------------|--------------|-----------|
| 단일 인스턴스 | 12.2 | 0.35 | 0 | 37 | 49.48 |
| Nginx + 인스턴스 2개 | 29.9 | 165.12 | 5.5 | 54 | 27.47 |
| Nginx + 인스턴스 3개 | 41.8 | 248.20 | 17 | 68.5 | 19.93 |

<br>

Nginx + 인스턴스 2개

<img width="1609" height="832" alt="image" src="https://github.com/user-attachments/assets/a4d4330b-d271-45cd-bd03-03791de4c9f6" />
파란색 네모 -> User 75 분홍색 네모 -> User : 50 보라색 네모 -> USer : 25
```
java.sql.SQLTransientConnectionException:
HikariPool-1 - Connection is not available, request timed out after 34007ms (total=49, active=48, idle=1, waiting=50)
```
User가 50이상일 경우 모든 DB connection 수 사용 -> 그 이상의 요청이 오는 경우 waiting -> 에러율 증가

<br>
Nginx + 인스턴스 3개

<img width="800" height="500" alt="image" src="https://github.com/user-attachments/assets/b95911e5-a2b1-4c98-beff-58588e11eea0" /> <img width="800" height="500" alt="image" src="https://github.com/user-attachments/assets/9001626d-bf8f-47d6-9eac-4b27601c7004" />

8080 인스턴스에 요청이 압도적으로 몰리고 있는 불균형 상황(보라색 네모) <br>
- pending 수가 약 90까지 치솟음 <br>
- 활성 커넥션 수가 풀 최대 사이즈(50)에 육박 -> 거의 사용 중<br>
- weight를 따로 주지 않았음에도 nginx가 불균등하게 트래픽을 분배한다고 판단 -> least_conn 방식 추가


<br>

| TPS (평균) | 응답시간 평균 (ms) | 최소 TPS (ms) | 최대 TPS (ms) | 에러율 (%) |
|-----------|-------------------|--------------|--------------|-----------|
| 41.8 | 248.20 | 17 | 68.5 | 19.93 |
| 34.5 | 1051.39 | 9 | 56.5 | 15.00 |

least_conn 추가 후 에러율 약 5% 감소


