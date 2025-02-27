-- Copyright (c) 2019 The DAML Authors. All rights reserved.
-- SPDX-License-Identifier: Apache-2.0

module DA.Daml.LF.Proto3.Error
    ( Error(..)
    ) where

import qualified Data.Text as T
import Data.Int (Int32)

import DA.Daml.LF.Ast

data Error
  = MissingField String
  | UnknownEnum String Int32
  | ParseError String
  | DuplicateModule ModuleName
  | DuplicateDataType TypeConName
  | DuplicateValue ExprValName
  | EDuplicateTemplate TypeConName
  | DuplicateChoice ChoiceName
  | UnsupportedMinorVersion T.Text
  | BadStringId Int32
  | BadDottedNameId Int32
  | ExpectedTCon Type
  deriving (Show, Eq)
