package com.deciphernow.greymatter

import eu.timepit.refined.W
import eu.timepit.refined.api.{ Refined, RefinedTypeOps }
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.string.MatchesRegex

package object data {
  type ParentOid = String Refined MatchesRegex[W.`"^[a-zA-Z0-9]{16}$"`.T]
  object ParentOid extends RefinedTypeOps[ParentOid, String]

  type Action = String Refined MatchesRegex[W.`"^[C{1}|D{1}|P{1}|U{1}|R{1}|X{1}]$"`.T]
  object Action extends RefinedTypeOps[Action, String]

  type Size = Long Refined NonNegative
  object Size extends RefinedTypeOps[Size, Long]
}
