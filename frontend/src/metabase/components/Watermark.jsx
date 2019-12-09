import React, { Component, PropTypes } from 'react';
import { connect } from "react-redux";

import MetabaseSettings from "metabase/lib/settings";

import { getUser } from "metabase/selectors/user";

const buttonStyle = {
  display:"none"
};

export const formatDate = (date, fmt) => {
  if (/(y+)/.test(fmt)) {
    fmt = fmt.replace(RegExp.$1, (date.getFullYear() + '').substr(4 - RegExp.$1.length))
  }
  const o = {
    'M+': date.getMonth() + 1,
    'd+': date.getDate(),
    'h+': date.getHours(),
    'm+': date.getMinutes(),
    's+': date.getSeconds()
  };
  for (const k in o) {
    if (new RegExp(`(${k})`).test(fmt)) {
      const str = o[k] + '';
      fmt = fmt.replace(RegExp.$1, (RegExp.$1.length === 1) ? str : addZero(str));
    }
  }
  return fmt;
};

function addZero(str) {
  return ('00' + str).substr(str.length)
}

export function watermark (props) {
  let follow = '';

  if (props) {
    let user = props.user;
    if (user) {
      follow = user.first_name;
    } else {
      follow = MetabaseSettings.get("site_name","内部资料");
    }
  }
  var canvas = document.getElementById('waterMark');
  // canvas.width = window.innerWidth / 4;
  // 文本宽度+间隔宽度
  canvas.width = 300;
  var ctx = canvas.getContext('2d');
  ctx.font = '20px microsoft-yahei';
  const date = formatDate(new Date(), 'yyyy-MM-dd hh:mm');
  // const follow = localStorage.follow
  ctx.fillStyle = 'rgba(0, 0, 0, .15)';
  ctx.rotate(-Math.PI / 12);
  // 后面的100可以控制宽度
  if (follow.length > 4) {
    // 换行显示
    ctx.fillText(`${follow}`, 10, 100);
    ctx.fillText(`${date}`, 10, 120);
  } else {
    ctx.fillText(`${follow} ${date}`, 10, 100);
  }
  var src = canvas.toDataURL('image/png');
  var els = document.querySelectorAll(props.selector);
  if (els && els.length > 0) {
    els.forEach(function(element) {
      element.style.backgroundImage = `url('${src}')`;
    });
  }
}

@connect(
  state => ({ user: getUser(state) }),
  null,
)
class Watermark extends Component {

  constructor(props) {
    super(props);
    console.log("this.state.user is :", this.props.user);
  }

  componentDidMount() {
    if (MetabaseSettings.enableWatermark()) {
      watermark(this.props);
    }
  }

  render() {
    return (
      <div>
        <canvas id="waterMark" height="120" style={buttonStyle}></canvas>
      </div>
    );
  }
}

Watermark.propTypes = {
  selector: PropTypes.string.isRequired
};


export default Watermark;
