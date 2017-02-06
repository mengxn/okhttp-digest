package com.burgstaller.okhttp.digest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.util.Collections;

import javax.net.SocketFactory;

import okhttp3.Address;
import okhttp3.Authenticator;
import okhttp3.Connection;
import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;

public class DigestAuthenticatorTest {

    @Mock
    private Connection mockConnection;
    @Mock
    private Dns mockDns;
    @Mock
    private SocketFactory socketFactory;
    @Mock
    private Authenticator proxyAuthenticator;
    @Mock
    private ProxySelector proxySelector;
    @Mock
    Proxy proxy;
    private Route mockRoute;
    private DigestAuthenticator authenticator;

    @Before
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        // setup some dummy data so that we dont get NPEs
        Address address = new Address("localhost", 8080, mockDns, socketFactory, null, null,
                null, proxyAuthenticator, null, Collections.singletonList(Protocol.HTTP_1_1),
                Collections.singletonList(ConnectionSpec.MODERN_TLS), proxySelector);
        InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", 8080);
        mockRoute = new Route(address, proxy, inetSocketAddress);
        given(mockConnection.route()).willReturn(mockRoute);

        authenticator = new DigestAuthenticator(new Credentials("user1", "user1"));
    }

    @Test
    public void testAuthenticate() throws Exception {
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .header("WWW-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", algorithm=MD5, qop=\"auth\"")
                .build();
        Request authenticated = authenticator.authenticate(mockRoute, response);

        assertThat(authenticated.header("Authorization"),
                matchesPattern("Digest username=\"user1\", realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5"));
    }

    @Test
    public void testAuthenticate__withProxy__shouldWork() throws Exception {
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(407)
                .header("Proxy-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", algorithm=MD5, qop=\"auth\"")
                .build();
        Request authenticated = authenticator.authenticate(mockRoute, response);

        assertThat(authenticated.header("Proxy-Authorization"),
                matchesPattern("Digest username=\"user1\", realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAuthenticate__withInvalidWWWAuthHeader__shouldThrowException() throws Exception {
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .header("WWW-Authenticate",
                        "Digest realm=\"myrealm\", algorithm=MD5, qop=\"auth\"")
                .build();
        try {
            authenticator.authenticate(null, response);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void testAuthenticate__withUriPathAndParameters() throws Exception {
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com/path/to/resource?parameter=value&parameter2=value2")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .header("WWW-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", algorithm=MD5, qop=\"auth\"")
                .build();
        Request authenticated = authenticator.authenticate(mockRoute, response);

        String authHeader = authenticated.header("Authorization");
        assertThat(authHeader,
                matchesPattern("Digest username=\"user1\", realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", uri=\"/path/to/resource\\?parameter=value&parameter2=value2\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5"));
    }

    @Test
    public void testAuthenticate_withMultipleAuthResponseHeaders_shouldWork() throws IOException {
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .addHeader("WWW-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", algorithm=MD5, qop=\"auth\"")
                .addHeader("WWW-Authenticate", "Basic realm=\"DVRNVRDVS\"")
                .build();
        Request authenticated = authenticator.authenticate(mockRoute, response);

        assertThat(authenticated.header("Authorization"),
                matchesPattern("Digest username=\"user1\", realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5"));
    }

    @Test
    public void testAuthenticate_withWrongPassword_shouldNotRepeat() throws IOException {
        // given
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .header("Authorization", "Digest username=\"user1\", realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .addHeader("WWW-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"NnjGCdMhBQA=8ede771f94b593e46e5d0dd10b68313226c133f4\", algorithm=MD5, qop=\"auth\"")
                .addHeader("WWW-Authenticate", "Basic realm=\"DVRNVRDVS\"")
                .build();

        // when
        final Request result = authenticator.authenticate(mockRoute, response);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    public void testAuthenticate_withDifferentNonce_shouldNotRetry() throws IOException {
        // given
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .header("Authorization", "Digest username=\"user1\", realm=\"myrealm\", nonce=\"AAAAAAA\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .addHeader("WWW-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"BBBBBB\", algorithm=MD5, qop=\"auth\"")
                .addHeader("WWW-Authenticate", "Basic realm=\"DVRNVRDVS\"")
                .build();

        // when
        final Request authenticated = authenticator.authenticate(null, response);

        // then
        assertThat(authenticated, is(nullValue()));
    }

    @Test
    public void testAuthenticate_withDifferentNonceAndStale_shouldRetry() throws IOException {
        // given
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .header("Authorization", "Digest username=\"user1\", realm=\"myrealm\", nonce=\"AAAAAAA\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .addHeader("WWW-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"BBBBBB\", algorithm=MD5, qop=\"auth\", stale=true")
                .addHeader("WWW-Authenticate", "Basic realm=\"DVRNVRDVS\"")
                .build();

        // when
        final Request authenticated = authenticator.authenticate(mockRoute, response);

        // then
        assertThat(authenticated.header("Authorization"),
                matchesPattern("Digest username=\"user1\", realm=\"myrealm\", nonce=\"BBBBBB\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5"));
    }

    @Test
    public void testAuthenticate_withDifferentNonceAndStaleAndQuotes_shouldRetry() throws IOException {
        // given
        Request dummyRequest = new Request.Builder()
                .url("http://www.google.com")
                .header("Authorization", "Digest username=\"user1\", realm=\"myrealm\", nonce=\"AAAAAAA\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5")
                .get()
                .build();
        Response response = new Response.Builder()
                .request(dummyRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .addHeader("WWW-Authenticate",
                        "Digest realm=\"myrealm\", nonce=\"BBBBBB\", algorithm=MD5, qop=\"auth\", stale=\"true\"")
                .addHeader("WWW-Authenticate", "Basic realm=\"DVRNVRDVS\"")
                .build();

        // when
        final Request authenticated = authenticator.authenticate(mockRoute, response);

        // then
        assertThat(authenticated.header("Authorization"),
                matchesPattern("Digest username=\"user1\", realm=\"myrealm\", nonce=\"BBBBBB\", uri=\"/\", response=\"[0-9a-f]+\", qop=auth, nc=00000001, cnonce=\"[0-9a-f]+\", algorithm=MD5"));
    }
}