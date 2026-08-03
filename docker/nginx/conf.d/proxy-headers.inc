# Shared proxy headers. Named .inc rather than .conf so nginx.conf's `include conf.d/*.conf` does
# not try to load it as a top-level block; it is pulled in explicitly by each location.

# The application builds nothing from the request host — links come from LINKFLOW_BASE_URL — but
# the UI needs it for redirects after form posts, and Spring's ForwardedHeaderFilter reads it.
proxy_set_header Host              $host;

# Replaces any client-supplied X-Forwarded-For instead of appending to it.
#
# The conventional $proxy_add_x_forwarded_for preserves whatever the client sent and appends the
# real address. That is correct when another proxy sits in front, but here Nginx *is* the edge, so
# anything already in the header was invented by the caller. Discarding it means no forged value
# ever reaches the application, rather than relying on the application to walk past it.
#
# If a CDN or cloud load balancer is ever placed in front of this, switch to
# $proxy_add_x_forwarded_for and add that hop to LINKFLOW_TRUSTED_PROXIES — otherwise every
# request will be attributed to the CDN.
proxy_set_header X-Forwarded-For   $remote_addr;
proxy_set_header X-Real-IP         $remote_addr;

# TLS ends here, so without these the application sees plain HTTP and would decline to mark
# session cookies Secure and to emit HSTS.
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $host;
proxy_set_header X-Forwarded-Port  $server_port;

# Required for the upstream keepalive pool: HTTP/1.0 has no persistent connections, and a
# forwarded "Connection: close" would retire the pooled connection after every request.
proxy_http_version 1.1;
proxy_set_header Connection "";

# Redirects are the product here. Passing them through untouched keeps the Location header exactly
# as the application built it, rather than having Nginx rewrite it against the upstream name.
proxy_redirect off;
