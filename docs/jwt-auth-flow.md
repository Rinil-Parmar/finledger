# JWT Authentication Flow - FinLedger

A step-by-step guide to how JWT authentication works in this project,
from registration to every authenticated API call.

---

## 1. What is JWT?

JSON Web Token (JWT) is a compact, URL-safe token format. Instead of storing
sessions on the server (stateful), the server issues a signed token that the
client sends with every request (stateless).

A JWT has three parts separated by dots:

```
header.payload.signature
```

- **Header** - algorithm and token type (`{"alg":"HS256","typ":"JWT"}`)
- **Payload** - claims (user data): subject, role, expiration, issued-at
- **Signature** - HMAC-SHA256 hash of header + payload using a server secret

The server never stores the token. It verifies the signature on each request
to confirm the token is genuine and unmodified.

---

## 2. Registration Flow

**Endpoint:** `POST /api/v1/auth/register`

```
Client                          Server
  |                               |
  |  POST /auth/register          |
  |  { email, password, role }    |
  |------------------------------>|
  |                               |  1. Check email not taken
  |                               |  2. Hash password with BCrypt
  |                               |  3. Save user to app_users table
  |                               |  4. Generate JWT with email + role
  |  { token, email, role }       |
  |<------------------------------|
  |                               |
  |  Store token in localStorage  |
```

### What happens at each step:

1. **Email uniqueness** - `AppUserRepository.existsByEmail()` checks the
   unique index on `app_users.email`

2. **Password hashing** - BCrypt with cost factor 10 (default). The raw
   password is never stored. BCrypt output looks like:
   `$2a$10$N9qo8uLOickgx2ZMRZoMy...`

3. **Database insert** - A row goes into `app_users` with the hashed password
   and the chosen role (USER or ADMIN)

4. **Token generation** - `JwtService.generateToken(email, role)` builds the JWT:
   ```java
   Jwts.builder()
       .subject(email)           // "sub" claim
       .claim("role", "USER")    // custom claim
       .issuedAt(now)            // "iat" claim
       .expiration(now + 1hr)    // "exp" claim
       .signWith(key)            // HMAC-SHA256 signature
       .compact()
   ```

---

## 3. Login Flow

**Endpoint:** `POST /api/v1/auth/login`

```
Client                          Server
  |                               |
  |  POST /auth/login             |
  |  { email, password }          |
  |------------------------------>|
  |                               |  1. Find user by email
  |                               |  2. BCrypt.matches(password, hash)
  |                               |  3. Generate JWT
  |  { token, email, role }       |
  |<------------------------------|
```

BCrypt comparison is constant-time to prevent timing attacks. The stored hash
contains the salt, so no separate salt column is needed.

---

## 4. Authenticated Request Flow

Every API call after login includes the token in the `Authorization` header.

```
Client                          Server
  |                               |
  |  GET /api/v1/accounts         |
  |  Authorization: Bearer <jwt>  |
  |------------------------------>|
  |                               |  JwtAuthenticationFilter runs:
  |                               |    1. Extract token from header
  |                               |    2. Parse and verify signature
  |                               |    3. Check expiration
  |                               |    4. Set SecurityContext
  |                               |  Controller handles request
  |  [accounts list]              |
  |<------------------------------|
```

### The filter chain in detail:

`JwtAuthenticationFilter` extends `OncePerRequestFilter` and runs before
Spring Security's authorization checks:

```java
// 1. Extract
String header = request.getHeader("Authorization");
String token = header.substring(7); // remove "Bearer "

// 2. Validate
if (jwtService.isValid(token)) {
    String email = jwtService.extractEmail(token);
    String role = jwtService.extractRole(token);

    // 3. Set security context
    var auth = new UsernamePasswordAuthenticationToken(
        email, null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role))
    );
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

Spring Security then checks the route's authorization rule:
- `/api/v1/auth/**` - permit all (no token needed)
- `POST /api/v1/accounts` - ADMIN only
- `/reconciliation/**`, `/outbox/**`, `/reversals/**` - ADMIN only
- All other `/api/v1/**` - any authenticated user

---

## 5. Role-Based Access Control (RBAC)

Two roles exist: `USER` and `ADMIN`.

| Action | USER | ADMIN |
|--------|------|-------|
| View accounts | Yes | Yes |
| Deposit / Withdraw | Yes | Yes |
| Transfer | Yes | Yes |
| Create accounts | No | Yes |
| View outbox events | No | Yes |
| Run reconciliation | No | Yes |
| Reverse transactions | No | Yes |

The role is embedded in the JWT as a custom claim. Spring Security reads it
as `ROLE_USER` or `ROLE_ADMIN` and enforces access with:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasRole("ADMIN")
```

---

## 6. Token Lifecycle

```
[Register/Login] --> [Token issued, 1hr TTL]
                           |
                     [Client stores in localStorage]
                           |
                     [Every request: Bearer header]
                           |
                     [Server validates signature + expiry]
                           |
              +-----------+-----------+
              |                       |
         [Valid]                 [Expired/Invalid]
              |                       |
         [Request OK]           [401 Unauthorized]
                                      |
                                [Client clears token]
                                [Redirects to login]
```

### Why localStorage?

- Simple and persistent across tabs
- Survives page refreshes
- The frontend reads the payload (base64-decoded, not verified) to display
  the user's email and role in the topbar
- On 401 response, the API client automatically clears the token

### Why not cookies?

- This is a single-page app calling its own API
- No need for automatic cookie attachment (we control every fetch call)
- Avoids CSRF complexity (no cookies = no CSRF)

---

## 7. Security Configuration

`SecurityConfig.java` sets up the full chain:

```java
http
    .csrf(csrf -> csrf.disable())          // Stateless API, no cookies
    .sessionManagement(sm ->
        sm.sessionCreationPolicy(STATELESS))  // No server sessions
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers("/actuator/**").permitAll()
        .requestMatchers("/ping").permitAll()
        .requestMatchers(POST, "/api/v1/accounts").hasRole("ADMIN")
        // ... more rules
        .anyRequest().authenticated()
    )
    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
```

Key decisions:
- **CSRF disabled** - safe because we use Bearer tokens, not cookies
- **Stateless sessions** - no `JSESSIONID`, no server memory per user
- **Filter order** - JWT filter runs before Spring's default auth filter

---

## 8. The Signing Key

The secret key must be at least 256 bits (32 bytes) for HMAC-SHA256.

```yaml
finledger:
  jwt:
    secret: ${JWT_SECRET:finledger-dev-secret-key-change-in-production-min-32-chars!!}
    expiration-ms: ${JWT_EXPIRATION_MS:3600000}   # 1 hour
```

- In development: uses the default string (fine for local testing)
- In production: set `JWT_SECRET` environment variable to a random 32+ char string
- The key is converted to bytes and wrapped with `Keys.hmacShaKeyFor()`

---

## 9. Testing with Auth

Existing integration tests bypass auth using `TestSecurityConfig`:

```java
@TestConfiguration
public class TestSecurityConfig {
    @Bean @Order(1)
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

This config is imported in every test class and takes priority (`@Order(1)`)
over the real security config, so tests can call any endpoint without tokens.

---

## 10. Try It Yourself

```bash
# 1. Register an admin user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","password":"secret123","role":"ADMIN"}'

# Response: {"token":"eyJhbG...","email":"admin@test.com","role":"ADMIN"}

# 2. Use the token
TOKEN="eyJhbG..."   # paste the token from step 1

curl http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN"

# 3. Decode the token (base64 the middle part)
echo "eyJhbG..." | cut -d. -f2 | base64 -d
# {"sub":"admin@test.com","role":"ADMIN","iat":1730000000,"exp":1730003600}
```

---

## File Map

| File | Purpose |
|------|---------|
| `src/main/java/com/finledger/auth/AppRole.java` | Enum: USER, ADMIN |
| `src/main/java/com/finledger/auth/AppUser.java` | JPA entity for app_users |
| `src/main/java/com/finledger/auth/AppUserRepository.java` | DB queries (findByEmail) |
| `src/main/java/com/finledger/auth/JwtService.java` | Token generation + validation |
| `src/main/java/com/finledger/auth/JwtAuthenticationFilter.java` | Request filter, sets SecurityContext |
| `src/main/java/com/finledger/auth/SecurityConfig.java` | Route rules, RBAC, filter chain |
| `src/main/java/com/finledger/auth/AuthController.java` | /register and /login endpoints |
| `src/main/resources/db/migration/V9__auth_users.sql` | Database table for credentials |
| `frontend/src/api/client.ts` | Frontend token storage + Bearer header |
| `frontend/src/pages/Login.tsx` | Login/register UI |
