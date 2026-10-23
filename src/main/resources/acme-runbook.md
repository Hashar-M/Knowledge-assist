# Orbit Payments Platform: Operations Runbook (fictional, for learning)

## Deployments
Production deploys happen Tuesday and Thursday, 10:00-16:00 UTC. No deploys on
Friday. Every deploy needs one approving review from the owning team.

## On-call
The payments on-call rotation changes every Monday at 09:00 UTC. The primary
on-call carries the pager; the secondary is the escalation point after 15 minutes.

## Dead-letter handling
Failed payment events are written to the topic `payments.events.dlq`.
Messages in `payments.events.dlq` are kept for 14 days and then deleted.
To replay messages, open a ticket labelled `DLQ-REPLAY`. A replay must be
approved by the on-call payments lead. Replays are executed with the internal
tool `orbit-replayctl`, in batches of at most 500 messages.

## Rollbacks
A rollback is allowed without approval if error rate exceeds 2% for 5 minutes.
