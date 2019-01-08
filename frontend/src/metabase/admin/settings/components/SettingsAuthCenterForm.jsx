import React, { Component } from "react";
import { t } from "c-3po";

import SettingsBatchForm from "./SettingsBatchForm";

export default class SettingsAuthCenterForm extends Component {
  render() {
    return (
      <SettingsBatchForm
        {...this.props}
        breadcrumbs={[
          [t`Authentication`, "/admin/settings/authentication"],
          [t`权限中心`],
        ]}
        enabledKey="auth-center-enabled"
        layout={[
          {
            title: t`Server Settings`,
            settings: [
              "auth-center-enabled",
              "auth-center-host",
            ],
          },
        ]}
        updateSettings={this.props.updateAuthCenterSettings}
      />
    );
  }
}
