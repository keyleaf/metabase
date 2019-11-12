import React, { Component } from "react";
import PropTypes from "prop-types";

import Toggle from "metabase/components/Toggle";

export default class PulseEditShowAttachmentsOnly extends Component {
  static propTypes = {
    pulse: PropTypes.object.isRequired,
    setPulse: PropTypes.func.isRequired,
  };

  toggle = () => {
    const { pulse, setPulse } = this.props;
    setPulse({ ...pulse, show_attach_only: !pulse.show_attach_only });
  };

  render() {
    const { pulse } = this.props;
    return (
      <div className="py1">
        <h2>只显示附件内容</h2>
        <p className="mt1 h4 text-bold text-medium">
          只显示附件，不显示明细内容。（仅对列表格式的报表有效）
        </p>
        <div className="my3">
          <Toggle value={pulse.show_attach_only || false} onChange={this.toggle} />
        </div>
      </div>
    );
  }
}
