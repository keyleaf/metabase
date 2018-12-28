import React, { Component } from "react";
import { Link } from "react-router";
import { t } from "c-3po";

class SettingsAuthenticationOptions extends Component {
  render() {
    return (
      <ul className="text-measure">
        <li>
          <div className="bordered rounded shadowed bg-white p4">
            <h2>{t`Sign in with Google`}</h2>
            <p
            >{t`Allows users with existing Metabase accounts to login with a Google account that matches their email address in addition to their Metabase username and password.`}</p>
            <Link
              className="Button"
              to="/admin/settings/authentication/google"
            >{t`Configure`}</Link>
          </div>
        </li>

        <li className="mt2">
          <div className="bordered rounded shadowed bg-white p4">
            <h2>{t`LDAP`}</h2>
            <p
            >{t`Allows users within your LDAP directory to log in to Metabase with their LDAP credentials, and allows automatic mapping of LDAP groups to Metabase groups.`}</p>
            <Link
              className="Button"
              to="/admin/settings/authentication/ldap"
            >{t`Configure`}</Link>
          </div>
        </li>

        <li className="mt3">
          <div className="bordered rounded shadowed bg-white p4">
            <h2>{t`权限中心`}</h2>
            <p
            >{t`对接权限中心`}</p>
            <Link
              className="Button"
              to="/admin/settings/authentication/auth"
            >{t`Configure`}</Link>
          </div>
        </li>
      </ul>
    );
  }
}

export default SettingsAuthenticationOptions;
