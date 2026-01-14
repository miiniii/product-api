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
<br>
<img width="800" height="500" alt="image" src="https://github.com/user-attachments/assets/01b3d65b-0637-47d5-8392-5dded9b6b9c1" />
<img width="800" height="500" alt="image" src="https://github.com/user-attachments/assets/9001626d-bf8f-47d6-9eac-4b27601c7004" />

8080 인스턴스에 요청이 압도적으로 몰리고 있는 불균형 상황(보라색 네모) <br>
- (첫번째 사진) 활성 커넥션 수가 풀 최대 사이즈(50)에 육박 -> 거의 사용 중 <br>
- (두번째 사진) pending 수가 약 90까지 치솟음 <br>
- weight를 따로 주지 않았음에도 nginx가 불균등하게 트래픽을 분배한다고 판단 -> least_conn 방식 추가


<br>

| TPS (평균) | 응답시간 평균 (ms) | 최소 TPS (ms) | 최대 TPS (ms) | 에러율 (%) |
|-----------|-------------------|--------------|--------------|-----------|
| 41.8 | 248.20 | 17 | 68.5 | 19.93 |
| 34.5 | 1051.39 | 9 | 56.5 | 15.00 |

least_conn 추가 후 에러율 약 5% 감소

<br>

Nginx + 인스턴스 3개 + least_conn 추가
<img width="800" height="500" alt="image" src="https://github.com/user-attachments/assets/e016dea1-c70d-48be-b3a4-11d86d8f71fe" />

에러율은 약 5% 감소했지만 응답시간이 증가 -> 3개 인스턴스 connection이 거의 다 사용중이기 때문이라 추측 <br>
커넥션 풀 포화 -> 대기 요청 증가 -> 평균 응답시간 증가 -> TPS 감소

추가 ) Nginx + 인스턴스 3개 로그

```
2025-06-18T02:39:02.469Z ERROR 1 --- [test-repo-java] [io-8080-exec-13] o.a.c.c.C.[.[.[/].[dispatcherServlet]:
Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed:
java.lang.RuntimeException: java.lang.OutOfMemoryError: Java heap space] with root cause

java.lang.OutOfMemoryError: Java heap space

java.sql.SQLTransientConnectionException:
HikariPool-1 - Connection is not available, request timed out after 48543ms (total=26, active=25, idle=1, waiting=0)
```
OOM 발생 -> max pool size 50임에도 26~33개 생성 -> 메모리가 부족해서 커넥션을 더 못 만드는 것으로 판단 <br>
(local 환경 : MacBook M1 Pro // AWS : t2.micro) 

## DB 부하 테스트(특정 상품 1개 조회)
### AWS(EC2)
✔️t2.micro <br>
✔️Ngrinder(controller, agent)   ✔️MySql

인덱스 적용 여부
Data : 16,000 
|구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|----------|------------|-------------------|-------------------|-------------------|-----------|
| 인덱스 X | 165.7 | 280.36 | 0 | 226.0 | 0.29 |
| 인덱스 O | 240 | 114.93 | 0.5 | 1120.0 | 1.45 |

<br>

| 인덱스 ❌ | 인덱스 ⭕ |
|------------------------|------------------------|
|<img width="791" height="150" alt="image" src="https://github.com/user-attachments/assets/505aea81-7da1-4266-9343-e6c2bf8c3b98" />|<img width="1029" height="107" alt="image" src="https://github.com/user-attachments/assets/1e78f508-59ba-42f9-84e9-b3954872961b" />|

- actual time이 약 1400배 빨라짐(평균 응답 시간이 약 50% 감소) <br>
- 인덱스 추가 후, TPS가 약 45% 증가 : DB가 풀 스캔 대신 인덱스를 통해 빠르게 결과를 찾았기 때문 <br>

<br>

<img width="363" height="291" alt="image" src="https://github.com/user-attachments/assets/96b44052-7c8d-44ba-9b3c-192868600134" />

보라색 네모 : 인덱스 X 
노란색 네모 : 인덱스 O
- 인덱스가 없을 때는 최대 풀 사이즈에 근접하게 연결이되나, 인덱스가 있을 경우 대부분 즉시 처리되기때문에 거의 사용 안되는 것으로 보임

<br>

<img width="900" height="600" alt="image" src="https://github.com/user-attachments/assets/4919a227-318a-417f-8ee0-fe72f6f3d20b" />

- 인덱스 추가로 인해 DB 응답 속도가 빨라지며, 애플리케이션이 더 많은 요청 처리 -> CPU 사용률 증가
- CPU 사용률이 급증하면서 일부 요청이 처리 지연되어 에러율이 올라갔을거라 추측

<br>

## Redis Cache 적용 테스트
<br>

| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|----------|------------|-------------------|-------------------|-------------------|-----------|
| Redis X | 8.3 | 0.44 | 0 | 25.0 | 49.48 |
| Redis O | 241.5 | 111.08 | 0 | 994.0 | 1.55 |

<img width="600" height="380" alt="image" src="https://github.com/user-attachments/assets/daa5f6af-4319-4b40-86a1-13272948a51b" />
<img width="400" height="300" alt="image" src="https://github.com/user-attachments/assets/9e056dc6-d187-406f-9d7f-db003af99b53" />

- (첫번째 사진) 모든 요청이 처음에 DB로 가기 때문에 CPU 사용률 일시적으로 증가 <br>
- (두번째 사진) Redis에 캐싱된 후부터는, Redis에서 바로 조회(Redis 히트율 1) <br>

### Redis 네트워크 통신
TTL : (10m)
<br>

| 구성 환경 | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|----------|------------|-------------------|-------------------|-------------------|-----------|
| 로컬 통신 | 230.0 | 66.83 | 0 | 1223.0 | 2.39 |
| 원격 통신 | 241.5 | 111.08 | 0 | 994.0 | 1.55 |

- 로컬 통신 : Docker 커스텀 네트워크(app-net)를 사용하여 컨테이너 간 통신
- 원격 통신 : 서로 다른 인스턴스 간 Redis 통신

```
흠..
```

## 락 비교
✔️Application Instance(3) ✔️Ngnix  ✔️Duration : 20s  ✔️Stock Quantity : 50
<br>

### 낙관적락(@Version)

```
확인해봐야할 사항) 락 충돌, 재시도 횟수, 쿼리 처리 속도

if ) TPS는 높지만 재시도가 많다면 시스템에 낭비가 생기고 있는 것
    TPS는 잘 나오는데 평균 응답시간이 튄다면 DB 충돌 또는 락 재시도가 의심
    낙관적 락은 업데이트 쿼리 실패 → 재요청이므로 쿼리량이 급증할 수 있음 (update나 rollback이 이상하게 늘어난다면 낙관적 락 충돌 가능성이 높음)
```

| VUsers | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|--------|------------|-------------------|-------------------|-------------------|-----------|
| 50 | 82.3 | 258.65 | 0 | 164 | 70.39 |
| 75 | 144.9 | 386.01 | 92 | 220.5 | 38.36 |

| VUsers(50) | VUsers(75) |
|------------------------|------------------------|
|<img width="1364" height="823" alt="image" src="https://github.com/user-attachments/assets/7bb0923b-d87b-4d69-ba99-3f561ebf7f60" />|<img width="1326" height="818" alt="image" src="https://github.com/user-attachments/assets/ce75dee9-d33d-43f7-9d6f-b9627d587a1c" />|
|- 특정 인스턴스에 집중적으로 몰려서 락 충돌 발생한것으로 판단 <br> - 충돌이 몰리면서 재시도 전에 실패하거나 타임 아웃 <br> - 실패가 많아 에러율 높음 | - 요청이 여러 인스턴스로 분산되어 일부만 락 충돌 <br> - 분산된 요청이 재시도 기회를 갖고 처리에 성공 <br> - 재시도 후 처리 성공 → 에러율 낮음 <br> |
|<img width="788" height="316" alt="image" src="https://github.com/user-attachments/assets/aa822825-561a-448b-8b17-d98af8f7e668" />|<img width="799" height="307" alt="image" src="https://github.com/user-attachments/assets/0d935025-1d81-46a4-a21c-d5180f07ff23" />|
|row_lock_waits 가파르게 증가|row_lock_waits 가파르게 증가|
|<img width="798" height="615" alt="image" src="https://github.com/user-attachments/assets/730cbc32-457e-4456-9244-5b7424d5e25a" />|<img width="799" height="606" alt="image" src="https://github.com/user-attachments/assets/f7e1fdd2-fe13-4b62-8615-7c1f726f9ade" />|

<br>

### 비관적락
JPA @Lock(LockModeType.PESSIMISTIC_WRITE) 사용
✔️Application Instance(3) ✔️Ngnix  ✔️Duration : 25s  ✔️Stock Quantity : 50
<br>
| VUsers | TPS (평균) | 응답시간 평균 (ms) | 응답시간 최소 (ms) | 응답시간 최대 (ms) | 에러율 (%) |
|--------|------------|-------------------|-------------------|-------------------|-----------|
| 50 | 343.2 | 169.5 | 247.5 | 443.5 | 0 |
| 75 | 278.2 | 242.93 | 211.5 | 348 | 0 |







