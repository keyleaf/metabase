/* @flow */

import React, { Component } from "react";
import styles from "./NightModeIcon.css";

import PopoverWithTrigger from "metabase/components/PopoverWithTrigger";
import Tooltip from "metabase/components/Tooltip";
import Icon from "metabase/components/Icon";
import { t } from "ttag";
import cx from "classnames";

const OPTIONS = [
  { name: t`白天模式`, theme: "sun" },
  { name: t`夜间模式`, theme: "moon" },
  { name: t`蓝色模式`, theme: "blue" },
];

export default class NightModeIcon extends Component {
  
  render() {
    const { theme, onThemeModeChange } = this.props;
    return (
      <PopoverWithTrigger
        ref="popover"
        triggerElement={
          theme == null ? (
            <Tooltip tooltip={t`切换主题`}>
              <Icon name="sun" />
            </Tooltip>
          ) : (
            <Tooltip
              tooltip={
                t`当前主题` +
                " " +
                theme
              }
            >
              <Icon name={theme} />
            </Tooltip>
          )
        }
        targetOffsetY={10}
      >
        <div className={styles.popover}>
          <div className={styles.title}>切换主题</div>
          <RefreshOptionList>
            {OPTIONS.map(option => (
              <RefreshOption
                key={option.theme}
                name={option.name}
                theme={option.theme}
                selected={option.theme === theme}
                onClick={() => {
                  // this.refs.popover.close();
                  onThemeModeChange(option.theme);
                }}
              />
            ))}
          </RefreshOptionList>
        </div>
      </PopoverWithTrigger>
    );
  }
}

const RefreshOptionList = ({ children }) => <ul>{children}</ul>;

const RefreshOption = ({ name, theme, selected, onClick }) => (
  <li
    className={cx(styles.option, styles[theme == null ? "sun" : "moon"], {
      [styles.selected]: selected,
    })}
    onClick={onClick}
  >
    <Icon name={name} size={14} />
    <span className={styles.name}>{name.split(" ")[0]}</span>
    <span className={styles.nameSuffix}> {name.split(" ")[1]}</span>
  </li>
);
