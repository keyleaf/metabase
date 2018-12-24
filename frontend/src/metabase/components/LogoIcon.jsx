import React, { Component } from "react";
import PropTypes from "prop-types";

export default class LogoIcon extends Component {
  static defaultProps = {
    size: 37,
  };

  static propTypes = {
    size: PropTypes.number,
    width: PropTypes.number,
    height: PropTypes.number
  };

  render() {
    let {height, width, size } = this.props;
    return (
      <img
        width={width || size}
        height={height || size} src="../app/assets/img/mam-logo.svg"></img>
    );
  }
}
