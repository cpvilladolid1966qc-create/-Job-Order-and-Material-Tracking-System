# Spring Mockito Bean Import

For Spring Boot 4 tests, `@MockitoBean` is imported from Spring Test’s bean-override package.

## What Happened

In Style Maria task t10, a new full-context staff invitation flow needed a mocked `JavaMailSender`. The initial test-only import from a Boot package failed compilation. Changing the import to `org.springframework.test.context.bean.override.mockito.MockitoBean` compiled, and the mail-capturing integration flow passed.

## Takeaway

Before using Spring’s test bean overrides, prefer the package exported by the resolved Spring Framework version: `org.springframework.test.context.bean.override.mockito.MockitoBean`. Keep mocked mail at the test boundary and validate the invite through the HTTP/session path.

## History
- 2026-11-04 (stylemaria/t10): initial
