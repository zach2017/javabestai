# Keycloak OIDC Demo — Spring Boot Resource Server + Go Client

End-to-end demo:

- **Keycloak 26** in Docker (realm, client, roles, users auto-imported)
- **Java 21 / Spring Boot 3.5.13** REST API acting as an **OAuth2 Resource Server** (validates JWT bearer tokens)
- **Go client** that obtains an OIDC access token from Keycloak and calls the API

## Layout

```
keycloak-oidc-demo/
├── docker-compose.yml
├── keycloak/
│   └── realm-export.json     # demo realm, demo-client, Admin/User roles, admin & user accounts
├── java-api/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/demo/api/
│       │   ├── ApiApplication.java
│       │   ├── SecurityConfig.java     # Resource Server config + Keycloak role mapper
│       │   └── HelloController.java
│       └── resources/application.yml
└── go-client/
    ├── go.mod
    └── main.go
```

## What gets created in Keycloak

| Item     | Value                  |
| -------- | ---------------------- |
| Realm    | `demo`                 |
| Client   | `demo-client` (confidential, Direct Access Grants enabled) |
| Secret   | `demo-client-secret`   |
| Roles    | `Admin`, `User`        |
| User #1  | `admin` / `password` — roles: `Admin`, `User` |
| User #2  | `user` / `password` — roles: `User` |

Keycloak admin console: <http://localhost:8080> (admin / admin)

## 1. Start Keycloak

```bash
docker compose up -d
```

Wait ~20 seconds for the realm import to finish. You can verify:

```bash
curl http://localhost:8080/realms/demo/.well-known/openid-configuration | head
```

## 2. Run the Spring Boot Resource Server

```bash
cd java-api
./mvnw spring-boot:run
# or:  mvn spring-boot:run
```

API listens on **http://localhost:8081** with three endpoints:

| Endpoint        | Required role           |
| --------------- | ----------------------- |
| `GET /hello`    | any authenticated user  |
| `GET /hello/user`  | role `User`         |
| `GET /hello/admin` | role `Admin`        |

Without a bearer token you get `401`. With a token but missing role you get `403`.

## 3. Run the Go client

```bash
cd go-client
go run .                    # logs in as "user" / "password"
go run . admin password     # logs in as "admin" / "password"
```

The client:
1. POSTs to `…/realms/demo/protocol/openid-connect/token` with the **password grant**.
2. Receives an access token (JWT).
3. Calls `/hello`, `/hello/user`, `/hello/admin` with `Authorization: Bearer <token>`.

Expected output for the `user` account:

```
GET /hello       -> HTTP 200
GET /hello/user  -> HTTP 200
GET /hello/admin -> HTTP 403   (missing Admin role)
```

For `admin` all three succeed.

## How the JWT → role mapping works

Keycloak puts realm roles in the `realm_access.roles` claim:

```json
"realm_access": { "roles": ["Admin", "User", "default-roles-demo"] }
```

`SecurityConfig.KeycloakRealmRoleConverter` reads that claim and turns each role into a `ROLE_<n>` Spring authority, so `@PreAuthorize("hasRole('Admin')")` works without any extra config.

## Quick token sanity check (without the Go client)

```bash
TOKEN=$(curl -s -X POST \
  -d "grant_type=password" \
  -d "client_id=demo-client" \
  -d "client_secret=demo-client-secret" \
  -d "username=admin" \
  -d "password=password" \
  http://localhost:8080/realms/demo/protocol/openid-connect/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/hello/admin
```

## Notes

- The password grant is used here for demo simplicity. For production prefer **authorization code + PKCE** for user-facing apps and **client credentials** for service-to-service.
- Spring Boot 3.5.13 requires Java 21+.
- Keycloak runs in `start-dev` mode — do not use this in production.
