package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
)

const (
	keycloakURL  = "http://localhost:8080"
	realm        = "demo"
	clientID     = "demo-client"
	clientSecret = "demo-client-secret"
	apiURL       = "http://localhost:8081"
)

// TokenResponse mirrors Keycloak's /token endpoint response.
type TokenResponse struct {
	AccessToken      string `json:"access_token"`
	ExpiresIn        int    `json:"expires_in"`
	RefreshExpiresIn int    `json:"refresh_expires_in"`
	RefreshToken     string `json:"refresh_token"`
	TokenType        string `json:"token_type"`
	Scope            string `json:"scope"`
}

// getToken obtains an access token from Keycloak using the
// Resource Owner Password Credentials grant (good for demos / CLI tools).
func getToken(username, password string) (*TokenResponse, error) {
	tokenURL := fmt.Sprintf("%s/realms/%s/protocol/openid-connect/token", keycloakURL, realm)

	form := url.Values{}
	form.Set("grant_type", "password")
	form.Set("client_id", clientID)
	form.Set("client_secret", clientSecret)
	form.Set("username", username)
	form.Set("password", password)
	form.Set("scope", "openid profile email")

	req, err := http.NewRequest(http.MethodPost, tokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("token request failed: %w", err)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("token endpoint returned %d: %s", resp.StatusCode, string(body))
	}

	var token TokenResponse
	if err := json.Unmarshal(body, &token); err != nil {
		return nil, fmt.Errorf("decode token response: %w", err)
	}
	return &token, nil
}

// callAPI calls the Spring API endpoint with the OIDC bearer token.
func callAPI(endpoint, accessToken string) {
	req, err := http.NewRequest(http.MethodGet, apiURL+endpoint, nil)
	if err != nil {
		log.Printf("build request %s: %v", endpoint, err)
		return
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("Accept", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		log.Printf("call %s: %v", endpoint, err)
		return
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	fmt.Printf("GET %s -> HTTP %d\n%s\n\n", endpoint, resp.StatusCode, string(body))
}

func main() {
	// Defaults — override with: go run . <username> <password>
	username := "user"
	password := "password"
	if len(os.Args) > 1 {
		username = os.Args[1]
	}
	if len(os.Args) > 2 {
		password = os.Args[2]
	}

	log.Printf("Requesting token for user %q from %s/realms/%s ...", username, keycloakURL, realm)
	token, err := getToken(username, password)
	if err != nil {
		log.Fatalf("Failed to get token: %v", err)
	}
	log.Printf("Got access token (expires in %d seconds)\n\n", token.ExpiresIn)

	for _, ep := range []string{"/hello", "/hello/user", "/hello/admin"} {
		callAPI(ep, token.AccessToken)
	}
}
