/* @flow */

import React from "react";

import EmbedSelect from "./EmbedSelect";
import CheckBox from "metabase/components/CheckBox";
import { t } from "ttag";
import type { DisplayOptions } from "./EmbedModalContent";

type Props = {
  className?: string,
  displayOptions: DisplayOptions,
  onChangeDisplayOptions: (displayOptions: DisplayOptions) => void,
};

const THEME_OPTIONS = [
    { name: t`白天模式`, value: "sun", icon: "sun" },
    { name: t`夜晚模式`, value: "moon", icon: "moon" },
    { name: t`蓝色模式`, value: "blue", icon: "blue" },
];

const DisplayOptionsPane = ({
  className,
  displayOptions,
  onChangeDisplayOptions,
}: Props) => (
  <div className={className}>
    <div className="flex align-center my1">
      <CheckBox
        checked={displayOptions.bordered}
        onChange={e =>
          onChangeDisplayOptions({
            ...displayOptions,
            bordered: e.target.checked,
          })
        }
      />
      <span className="ml1">{t`Border`}</span>
    </div>
    <div className="flex align-center my1">
      <CheckBox
        checked={displayOptions.titled}
        onChange={e =>
          onChangeDisplayOptions({
            ...displayOptions,
            titled: e.target.checked,
          })
        }
      />
      <span className="ml1">{t`Title`}</span>
    </div>
    <div className="flex align-center my1">
      <CheckBox
        checked={displayOptions.footer}
        onChange={e =>
          onChangeDisplayOptions({
            ...displayOptions,
            footer: e.target.checked,
          })
        }
      />
      <span className="ml1">下标</span>
    </div>
    <EmbedSelect
      value={displayOptions.theme}
      options={THEME_OPTIONS}
      onChange={value =>
        onChangeDisplayOptions({ ...displayOptions, theme: value })
      }
    />
  </div>
);

export default DisplayOptionsPane;
