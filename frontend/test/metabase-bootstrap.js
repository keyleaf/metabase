import "babel-polyfill";
import "number-to-locale-string";
import "metabase/css/index.css";

window.MetabaseBootstrap = {
  enable_xrays: true,
  timezones: [
    "GMT",
    "UTC",
    "US/Alaska",
    "US/Arizona",
    "US/Central",
    "US/Eastern",
    "US/Hawaii",
    "US/Mountain",
    "US/Pacific",
    "America/Costa_Rica",
  ],
  available_locales: [["en", "English"]],
  // NOTE: update this when updating types.clj
  types: {
    "type/DruidHyperUnique": ["type/*"],
    "type/Longitude": ["type/Coordinate"],
    "type/IPAddress": ["type/TextLike"],
    "type/URL": ["type/Text"],
    "type/BigInteger": ["type/Integer"],
    "type/Category": ["type/Special"],
    "type/Owner": ["type/User"],
    "type/TextLike": ["type/*"],
    "type/Discount": ["type/Number"],
    "type/UNIXTimestampSeconds": ["type/UNIXTimestamp"],
    "type/PostgresEnum": ["type/Text"],
    "type/Time": ["type/DateTime"],
    "type/Integer": ["type/Number"],
    "type/Author": ["type/User"],
    "type/Cost": ["type/Number"],
    "type/Quantity": ["type/Integer"],
    "type/Number": ["type/*"],
    "type/JoinTimestamp": ["type/DateTime"],
    "type/Subscription": ["type/Category"],
    "type/State": ["type/Category", "type/Address", "type/Text"],
    "type/Address": ["type/*"],
    "type/Source": ["type/Category"],
    "type/Name": ["type/Category", "type/Text"],
    "type/Decimal": ["type/Float"],
    "type/Birthdate": ["type/Date"],
    "type/Date": ["type/DateTime"],
    "type/Text": ["type/*"],
    "type/FK": ["type/Special"],
    "type/SerializedJSON": ["type/Text", "type/Collection"],
    "type/MongoBSONID": ["type/TextLike"],
    "type/Duration": ["type/Number"],
    "type/Float": ["type/Number"],
    "type/Currency": ["type/Float"],
    "type/CreationTimestamp": ["type/DateTime"],
    "type/Email": ["type/Text"],
    "type/City": ["type/Category", "type/Address", "type/Text"],
    "type/Title": ["type/Category", "type/Text"],
    "type/Special": ["type/*"],
    "type/Dictionary": ["type/Collection"],
    "type/Description": ["type/Text"],
    "type/Company": ["type/Category"],
    "type/PK": ["type/Special"],
    "type/Latitude": ["type/Coordinate"],
    "type/Coordinate": ["type/Float"],
    "type/UUID": ["type/Text"],
    "type/Country": ["type/Category", "type/Address", "type/Text"],
    "type/Boolean": ["type/Category", "type/*"],
    "type/GrossMargin": ["type/Number"],
    "type/AvatarURL": ["type/URL"],
    "type/Share": ["type/Float"],
    "type/Product": ["type/Category"],
    "type/ImageURL": ["type/URL"],
    "type/Price": ["type/Number"],
    "type/UNIXTimestampMilliseconds": ["type/UNIXTimestamp"],
    "type/Collection": ["type/*"],
    "type/User": ["type/*"],
    "type/Array": ["type/Collection"],
    "type/Income": ["type/Number"],
    "type/Comment": ["type/Text"],
    "type/Score": ["type/Number"],
    "type/ZipCode": ["type/Address", "type/Text"],
    "type/DateTime": ["type/*"],
    "type/UNIXTimestamp": ["type/Integer", "type/DateTime"],
    "type/Enum": ["type/Category", "type/*"],
    "type/Desensitization": ["type/Text"],
  },
};
