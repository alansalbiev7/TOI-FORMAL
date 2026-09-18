package ru.smev.asg

import ru.smev.asg.verification.*

/** Reference in-process admission model. The authenticated approval service is external.
A caller-provided name is NOT an authentication or electronic-signature mechanism. */
final case class ReleaseContext(profileVersion: String, sourceVersion: String, targetVersion: String,
                                operation: String, bindings: Map[String, String])
final case class Approval(context: ReleaseContext, approver: String)
final case class ReleasedMapping private[asg] (context: ReleaseContext, approvedBy: String)

object ReleaseGate:
  def admit(context: ReleaseContext, evidence: Evidence, approval: Approval,
            authorizedApprovers: Set[String]): Either[String, ReleasedMapping] =
    if Vector(context.profileVersion, context.sourceVersion, context.targetVersion,
              context.operation).exists(_.trim.isEmpty) then Left("Incomplete context")
    else if evidence.bindings.keySet != Set("data", "shapes", "query") ||
            evidence.bindings.values.exists(v => !v.matches("[a-f0-9]{64}")) then Left("Incomplete evidence bindings")
    else if evidence.bindings != context.bindings then Left("Evidence belongs to different artifacts")
    else if !evidence.passes(Verification.Required) then Left("Checks did not pass")
    else if approval.context != context then Left("Approval belongs to a different context")
    else if !authorizedApprovers.contains(approval.approver) then Left("Approver is not authorized")
    else Right(ReleasedMapping(context, approval.approver))

  def usable(release: ReleasedMapping, context: ReleaseContext, revoked: Boolean): Boolean =
    !revoked && release.context == context
