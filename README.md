# Digital Banking — Money Transfer Service

Backend service สำหรับระบบโอนเงินระหว่างบัญชี พัฒนาด้วย Java 21 + Spring Boot 3.3.x

## 1. วิธีรันตั้งแต่ศูนย์
```bash
docker compose up -d --build
```
คำสั่งเดียวจบ — จะได้ app + SQL Server + Redis + IBM MQ ครบ พร้อม auto-provisioning ทั้งหมด (database, queue, permissions) รอจนทุก container ขึ้นสถานะ `healthy`/`Up` (เช็คได้ด้วย `docker compose ps`) แอปจะพร้อมใช้งานที่ `http://localhost:8080`

**ครั้งแรกจะใช้เวลานานหน่อย** (ต้อง build image ของแอปเอง + ดึง image SQL Server/IBM MQ) ครั้งถัดไปจะเร็วขึ้นมากเพราะมี cache

### ปิดระบบ
```bash
docker compose down        # ปิด container แต่เก็บข้อมูลไว้ (volume ยังอยู่)
docker compose down -v     # ปิด + ลบข้อมูลทั้งหมด (เริ่มนับหนึ่งใหม่)
```

---

## 2. วิธีรันเทส

```bash
mvn test
```

รันเฉพาะ unit test ของ `AccountService` (ทดสอบ logic ฝาก/ถอน/lock โดยใช้ Mockito mock ไม่ต้องพึ่ง Docker หรือ database จริง)

**Coverage:** ยังไม่ได้ตั้งค่าเครื่องมือวัด coverage (เช่น JaCoCo) — เป็นสิ่งที่ควรเพิ่มถ้ามีเวลาต่อ

---

## 3. API Documentation / Swagger UI

**ยังไม่ได้ implement** — ไม่ได้เพิ่ม dependency `springdoc-openapi` ไว้ตั้งแต่ตอนสร้างโปรเจกต์

**ทางเลือกตอนนี้:** ใช้ตัวอย่าง curl ในหัวข้อ 4 ด้านล่าง หรือ import เข้า Postman เอง

---

## 4. ตัวอย่างเรียก API ด้วย curl

> เขียนสำหรับ Git Bash / macOS / Linux (ถ้าใช้ PowerShell ต้อง escape `"` เป็น `\"`)

### 4.1 เปิดบัญชี
```bash
curl -i -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName": "สมชาย ใจดี", "currency": "THB", "initialBalance": 1000.00}'
```

### 4.2 ดูข้อมูลบัญชี
```bash
curl -i http://localhost:8080/api/v1/accounts/1
```

### 4.3 ดูยอดคงเหลือ
```bash
curl -i http://localhost:8080/api/v1/accounts/1/balance
```

### 4.4 ฝากเงิน
```bash
curl -i -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00}'
```

### 4.5 ถอนเงิน
```bash
curl -i -X POST http://localhost:8080/api/v1/accounts/1/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00}'
```

### 4.6 โอนเงิน
```bash
curl -i -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(date +%s)" \
  -d '{"fromAccountId": 1, "toAccountId": 2, "amount": 100.00, "currency": "THB"}'
```

### 4.7 ดูสถานะการโอน
```bash
curl -i http://localhost:8080/api/v1/transfers/1
```

### 4.8 ดู statement (รายการเดินบัญชี)
> **ยังไม่ได้ implement endpoint นี้** (`GET /accounts/{id}/transactions`) — ดูรายละเอียดในตารางสถานะงานด้านล่าง

---

## 5. ตารางสรุปสถานะงาน

### ทำเสร็จแล้ว ✅

| หมวด | รายการ |
|---|---|
| Infrastructure | Dockerfile ของแอป + wiring เข้า docker-compose.yml ครบ — `docker compose up -d --build` ได้ app + sqlserver + redis + ibmmq |
| Database | Liquibase migration ครบ 4 ตาราง (account, transfer, ledger_entry, outbox_event) |
| Account API | `POST /accounts`, `GET /accounts/{id}`, `GET /accounts/{id}/balance`, `POST /accounts/{id}/deposit`, `POST /accounts/{id}/withdraw` |
| Transfer API | `POST /transfers`, `GET /transfers/{id}` |
| Concurrency control | Redis distributed lock (SET NX PX + Lua script release), DB pessimistic lock (`WITH UPDLOCK`), lock ordering ป้องกัน deadlock ตอนโอนเงิน (ล็อกตาม id น้อย→มากเสมอ) |
| Idempotency | ตรวจสอบ `Idempotency-Key` + request hash (SHA-256), คืนผลลัพธ์เดิมถ้า key+payload ตรงกัน, ตอบ 409 ถ้า key ซ้ำแต่ payload ต่าง |
| Rate limiting | จำกัด 10 ครั้ง/60 วินาทีต่อบัญชีต้นทางตอนโอนเงิน (Redis INCR + TTL) ตอบ 429 พร้อม `Retry-After` |
| Error handling | RFC 7807 Problem Details ครบทุก error code ที่ระบุในสเปก (400, 404, 409, 415, 422, 429) พร้อม catch-all handler กันหลุด |
| Tracing | `X-Request-Id` header (รับจาก client หรือสร้างให้เอง) ส่งกลับทุก response ทั้ง success/error ผ่าน Servlet Filter + MDC |
| Messaging | Transactional Outbox pattern: บันทึก event ใน transaction เดียวกับการโอนเงิน, scheduled poller (`@Scheduled` ทุก 5 วินาที) ส่งเข้า IBM MQ ผ่าน JMS |
| Test | Unit test สำหรับ `AccountService` (3 เคส: ถอนสำเร็จ, ยอดไม่พอ, บัญชีถูกล็อก) ใช้ Mockito mock |

### ยังไม่ได้ทำ ❌

| หมวด | รายการ | เหตุผล |
|---|---|---|
| API | `GET /accounts/{id}/transactions` (statement) | หมดเวลา |
| API | `PATCH /accounts/{id}/status` (freeze/close) | หมดเวลา |
| Test | Integration test ด้วย Testcontainers | เขียนไว้แล้วแต่เจอปัญหา Testcontainers หา Docker Desktop บนเครื่อง Windows ไม่เจอ (`Could not find a valid Docker environment`) แก้ไม่ทันจึงตัดออกจาก scope |
| Test | Concurrency test (หลาย thread ฝาก/ถอนพร้อมกัน) | หมดเวลา — เคยทดสอบ manual ผ่าน Postman Collection Runner (rate limiting) แต่ไม่ได้เขียนเป็น automated test |
| Test | Idempotency test แบบ automated | เคยทดสอบผ่าน Postman/curl manual แล้วได้ผลถูกต้อง แต่ไม่ได้เขียนเป็น JUnit test |
| Documentation | OpenAPI spec / Swagger UI | หมดเวลา |
| Bonus | Reconciliation check (ผลรวม ledger = balance) | หมดเวลา |
| Bonus | Metrics (Prometheus), graceful shutdown, load test | หมดเวลา |
| Data | Seed data ตัวอย่างผ่าน Liquibase | หมดเวลา — ปัจจุบันต้องสร้างบัญชีเองผ่าน API ก่อนทดสอบ |

### ถ้ามีเวลาเพิ่ม จะทำอะไรต่อ (เรียงตามความสำคัญ)

1. แก้ปัญหา Testcontainers บน Windows แล้วทำ integration test ให้ครบ (full flow ผ่าน HTTP จริงบน container ชั่วคราว)
2. เขียน concurrency test ด้วย `ExecutorService` + `CountDownLatch` ยิง deposit/withdraw หลาย thread พร้อมกันที่บัญชีเดียว พิสูจน์ว่ายอดสุดท้ายถูกต้องและไม่ติดลบ
3. เขียน idempotency test อัตโนมัติ (ยิง request ซ้ำพร้อมกันหลาย thread ด้วย key เดียวกัน เช็คว่าหักเงินแค่ครั้งเดียว)
4. ทำ `Dockerfile` ของแอปเอง ใส่เข้า `docker compose.yml` ให้ `docker compose up` ได้ครบจริงตามสเปก
5. เพิ่ม `GET /accounts/{id}/transactions` และ `PATCH /accounts/{id}/status`
6. เพิ่ม springdoc-openapi สำหรับ Swagger UI
7. Reconciliation check endpoint/script

---

## 6. ข้อจำกัด / สมมติฐานที่ตั้งไว้

- **Account number generation:** สุ่มเลข 10 หลักแล้วเช็ค uniqueness เอง (ไม่ได้อิงตาม sequence ของ `id` ตามตัวอย่างในสเปก)
- **Currency ที่รองรับ:** จำกัดไว้แค่ `THB, USD, EUR, JPY, SGD` (hardcode เป็น whitelist ใน service layer)
- **Rate limiting:** ใช้ fixed window (ไม่ใช่ sliding window) เพื่อความง่าย — อาจมี edge case ที่ยิงได้เกินลิมิตเล็กน้อยตรงรอยต่อของ window
- **Outbox publisher:** polling interval ทุก 5 วินาที (ไม่ใช่ real-time) และยังไม่มี dead-letter handling ถ้า publish ล้มเหลวซ้ำๆ (จะพยายามใหม่ไปเรื่อยๆ ไม่มี limit จำนวนครั้ง retry)
- **IBM MQ permission:** ใช้ `PRINCIPAL('app')` แทน `GROUP('mqclient')` ในการให้สิทธิ์ queue เพราะ image เวอร์ชันที่ใช้มีพฤติกรรมต่างจาก documentation (ไม่สร้าง OS-level group ตามที่เอกสารเก่าระบุไว้)
- **Development only:** credential ทั้งหมด (SQL Server `sa`, IBM MQ `app`/`admin`) เป็นค่า hardcode สำหรับ local dev เท่านั้น ไม่ควรใช้ค่าแบบนี้ใน production