// Copyright (c) 2019 The DAML Authors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
import * as jtv from '@mojotech/json-type-validation';

/**
 * Interface for companion objects of serializable types. Its main purpose is
 * to describe the JSON encoding of values of the serializable type.
 */
export interface Serializable<T> {
  // NOTE(MH): This must be a function to allow for mutually recursive decoders.
  decoder: () => jtv.Decoder<T>;
}

/**
 * Identifier of a DAML template.
 */
export type TemplateId = {
  packageId: string;
  moduleName: string;
  entityName: string;
}

/**
 * Companion object of the `TemplateId` type.
 */
const TemplateId: Serializable<TemplateId> = {
  decoder: () => jtv.object({
    packageId: jtv.string(),
    moduleName: jtv.string(),
    entityName: jtv.string(),
  })
}

/**
 * Interface for objects representing DAML templates. It is similar to the
 * `Template` type class in DAML.
 */
export interface Template<T extends {}> extends Serializable<T> {
  templateId: TemplateId;
  Archive: Choice<T, {}>;
}

/**
 * Interface for objects representing DAML choices. It is similar to the
 * `Choice` type class in DAML.
 */
export interface Choice<T, C> extends Serializable<C> {
  template: Template<T>;
  choiceName: string;
}

/**
 * The counterpart of DAML's `()` type.
 */
export type Unit = {};

/**
 * Companion obect of the `Unit` type.
 */
export const Unit: Serializable<Unit> = {
  decoder: () => jtv.object({}),
}

/**
 * The counterpart of DAML's `Bool` type.
 */
export type Bool = boolean;

/**
 * Companion object of the `Bool` type.
 */
export const Bool: Serializable<Bool> = {
  decoder: jtv.boolean,
}

/**
 * The counterpart of DAML's `Int` type. We represent `Int`s as string in order
 * to avoid a loss of precision.
 */
export type Int = string;

/**
 * Companion object of the `Int` type.
 */
export const Int: Serializable<Int> = {
  decoder: jtv.string,
}

/**
 * The counterpart of DAML's `Decimal` type. We represent `Decimal`s as string
 * in order to avoid a loss of precision. The string must match the regular
 * expression `-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?`.
 */
export type Decimal = string;

/**
 * Companion object of the `Decimal` type.
 */
export const Decimal: Serializable<Decimal> = {
  decoder: jtv.string,
}

/**
 * The counterpart of DAML's `Text` type.
 */
export type Text = string;

/**
 * Companion object of the `Text` type.
 */
export const Text: Serializable<Text> = {
  decoder: jtv.string,
}

/**
 * The counterpart of DAML's `Time` type. We represent `Times`s as strings with
 * format `YYYY-MM-DDThh:mm:ss[.ssssss]Z`.
 */
export type Time = string;

/**
 * Companion object of the `Time` type.
 */
export const Time: Serializable<Time> = {
  decoder: jtv.string,
}

/**
 * The counterpart of DAML's `Party` type. We represent `Party`s as strings
 * matching the regular expression `[A-Za-z0-9:_\- ]+`.
 */
export type Party = string;

/**
 * Companion object of the `Party` type.
 */
export const Party: Serializable<Party> = {
  decoder: jtv.string,
}

/**
 * The counterpart of DAML's `[T]` list type. We represent lists using arrays.
 */
export type List<T> = T[];

/**
 * Companion object of the `List` type.
 */
export const List = <T>(t: Serializable<T>): Serializable<T[]> => ({
  decoder: () => jtv.array(t.decoder()),
});

/**
 * The counterpart of DAML's `Date` type. We represent `Date`s as strings with
 * format `YYYY-MM-DD`.
 */
export type Date = string;

/**
 * Companion object of the `Date` type.
 */
export const Date: Serializable<Date> = {
  decoder: jtv.string,
}

/**
 * The counterpart of DAML's `ContractId T` type. We represent `ContractId`s
 * as strings. Their exact format of these strings depends on the ledger the
 * DAML application is running on.
 */
export type ContractId<T> = string;

/**
 * Companion object of the `ContractId` type.
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export const ContractId = <T>(_t: Serializable<T>): Serializable<ContractId<T>> => ({
  decoder: jtv.string,
});

/**
 * The counterpart of DAML's `Optional T` type. Nested optionals are not yet
 * supported.
 */
export type Optional<T> = T | null;

/**
 * Companion object of the `Optional` type.
 */
export const Optional = <T>(t: Serializable<T>): Serializable<Optional<T>> => ({
  decoder: () => jtv.oneOf(jtv.constant(null), t.decoder()),
});

/**
 * The counterpart of DAML's `TextMap T` type. We represent `TextMap`s as
 * dictionaries.
 */
export type TextMap<T> = { [key: string]: T };

/**
 * Companion object of the `TextMap` type.
 */
export const TextMap = <T>(t: Serializable<T>): Serializable<TextMap<T>> => ({
  decoder: () => jtv.dict(t.decoder()),
});

// TODO(MH): `Numeric` type.

// TODO(MH): `Map` type.

/**
 * Type for a contract instance of a template type `T`. Besides the contract
 * payload it also contains meta data like the contract id, signatories, etc.
 *
 * Contract keys are not yet properly supported.
 */
export type Contract<T> = {
  templateId: TemplateId;
  contractId: ContractId<T>;
  signatories: Party[];
  observers: Party[];
  agreementText: Text;
  key: unknown;
  argument: T;
  witnessParties: Party[];
  workflowId?: string;
}

/**
 * Companion object of the `Contract` type.
 */
export const Contract = <T extends {}>(t: Template<T>): Serializable<Contract<T>> => ({
  decoder: () => jtv.object({
    templateId: TemplateId.decoder(),
    contractId: ContractId(t).decoder(),
    signatories: jtv.array(Party.decoder()),
    observers: jtv.array(Party.decoder()),
    agreementText: Text.decoder(),
    key: jtv.unknownJson(),
    argument: t.decoder(),
    witnessParties: jtv.array(Party.decoder()),
    workflowId: jtv.optional(jtv.string()),
  }),
});

/**
 * Type for queries against the `/contract/search` endpoint of the JSON API.
 * `Query<T>` is the type of queries that are valid when searching for
 * contracts of template type `T`.
 *
 * Comparison queries are not yet supported.
 *
 * NB: This type is heavily related to the `DeepPartial` type that can be found
 * in the TypeScript community.
 */
export type Query<T> = T extends object ? {[K in keyof T]?: Query<T[K]>} : T;
// TODO(MH): Support comparison queries.
