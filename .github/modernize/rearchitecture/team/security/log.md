## [t8] Auditing authentication, authorization, invitations, and sensitive APIs
- Codebase/domain discoveries: Reviewed route-role checks, session lifecycle, customer ownership filters, invitation token hashing/expiry/acceptance, and audit transaction coupling. Unmatched routes are not default-denied, although no currently mapped sensitive route was identified as falling through.
- Wrong assumptions and corrections: The directory is not a Git repository; the review could not distinguish the final patch from pre-existing source.
- Debugging dead-ends and what actually worked: The upstream frontend and dependency-remediation indexes plus the final CVE report provided implementation and scan context; no runtime validation was performed for this read-only review.
- Techniques/patterns worth reusing for future tasks: Review server route authorization independently of frontend visibility; record broad unmatched-route behavior separately from confirmed unprotected sensitive endpoints.
- Learnings consumed: (none)
