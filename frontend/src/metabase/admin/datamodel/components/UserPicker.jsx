/* eslint "react/prop-types": "warn" */
import React, { Component } from "react";
import PropTypes from "prop-types";

import TokenField from "metabase/components/TokenField";
import UserAvatar from "metabase/components/UserAvatar";

import MetabaseAnalytics from "metabase/lib/analytics";
import {formDomOnlyProps} from "metabase/lib/redux";

const VALID_EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default class UserPicker extends Component {
  static propTypes = {
    field: PropTypes.object.isRequired,
    recipients: PropTypes.array,
    recipientTypes: PropTypes.array.isRequired,
    users: PropTypes.array,
    isNewPulse: PropTypes.bool.isRequired,
    onRecipientsChange: PropTypes.func.isRequired,
    autoFocus: PropTypes.bool,
  };

  static defaultProps = {
    recipientTypes: ["user"],
    autoFocus: true,
  };

  constructor(props) {
    super(props);
    this.state = { recipients: props.recipients };
  }

  handleOnChange = newRecipients => {
    console.log("handleOnChange");
    console.log(this.props);
    console.log("handleOnChange newRecipients is :");
    console.log(newRecipients);
    this.setState({ recipients: newRecipients});
    this.props.onRecipientsChange(newRecipients);
    this._trackChange(newRecipients);
  };

  _trackChange(newRecipients) {
    const { recipients, isNewPulse } = this.props;

    // kind of hacky way to find the changed recipient
    const previous = new Set(recipients.map(r => JSON.stringify(r)));
    const next = new Set(newRecipients.map(r => JSON.stringify(r)));
    const recipient =
      [...next].filter(r => !previous.has(r))[0] ||
      [...previous].filter(r => !next.has(r))[0];

    MetabaseAnalytics.trackEvent(
      isNewPulse ? "PulseCreate" : "PulseEdit",
      newRecipients.length > recipients.length
        ? "AddRecipient"
        : "RemoveRecipient",
      recipient && (recipient.id ? "user" : "email"),
    );
  }

  render() {
    const { field, users, autoFocus } = this.props;
    const { recipients } = this.state;
    return (
      <TokenField
        value={recipients}
        options={
          users
            ? users.map(user => ({ label: user.common_name, value: user }))
            : []
        }
        onChange={this.handleOnChange}
        placeholder={
          recipients.length === 0
            ? "请选择要配置行级权限的用户"
            : null
        }
        autoFocus={autoFocus && recipients.length === 0}
        multi
        valueRenderer={value => value.common_name || value.email}
        optionRenderer={option => (
          <div className="flex align-center">
            <span className="text-white">
              <UserAvatar user={option.value} />
            </span>
            <span className="ml1 h4">{option.value.common_name}</span>
          </div>
        )}
        filterOption={(option, filterString) =>
          // case insensitive search of name or email
          ~option.value.common_name
            .toLowerCase()
            .indexOf(filterString.toLowerCase()) ||
          ~option.value.email.toLowerCase().indexOf(filterString.toLowerCase())
        }
        parseFreeformValue={inputValue => {
          if (VALID_EMAIL_REGEX.test(inputValue)) {
            return { email: inputValue };
          }
        }}
        updateOnInputBlur
        {...formDomOnlyProps(field)}
      />
    );
  }
}
