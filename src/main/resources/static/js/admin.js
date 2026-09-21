// OnO 관리자 화면 공통 동작. 프레임워크 없이 서버가 그린 HTML 위에 움직임만 얹는다.
(function () {
  'use strict';

  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // 행 전체를 눌러 상세로 이동한다. 행 안의 링크나 버튼을 누르면 그쪽이 우선이다.
  function bindRowLinks() {
    document.addEventListener('click', function (e) {
      var row = e.target.closest('tr[data-href]');
      if (!row || e.target.closest('a, button, input, select, label')) return;
      var href = row.getAttribute('data-href');
      if (e.metaKey || e.ctrlKey) {
        window.open(href, '_blank');
      } else {
        window.location.href = href;
      }
    });
  }

  // 카드와 표가 위에서부터 차례로 올라오게 한다.
  function reveal() {
    if (reduceMotion) return;
    var targets = document.querySelectorAll('[data-reveal] > *');
    targets.forEach(function (el, i) {
      el.style.setProperty('--i', Math.min(i, 12));
      el.classList.add('reveal');
    });
  }

  // 숫자는 0 에서 목표값까지 짧게 올라간다. 소수점 자릿수는 원래 글자를 따른다.
  function countUp() {
    var els = document.querySelectorAll('[data-count]');
    els.forEach(function (el) {
      var raw = el.textContent.trim().replace(/,/g, '');
      var target = parseFloat(raw);
      if (!isFinite(target) || reduceMotion || target === 0) return;
      var decimals = (raw.split('.')[1] || '').length;
      var start = null;
      var duration = 700;
      var format = function (v) {
        return v.toLocaleString('ko-KR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
      };
      el.textContent = format(0);
      function step(ts) {
        if (start === null) start = ts;
        var p = Math.min((ts - start) / duration, 1);
        var eased = 1 - Math.pow(1 - p, 3);
        el.textContent = format(target * eased);
        if (p < 1) requestAnimationFrame(step);
        else el.textContent = format(target);
      }
      requestAnimationFrame(step);
    });
  }

  // 밑줄이 선택된 탭 아래로 미끄러진다. 주소의 #hash 로 탭을 기억해서 새로고침해도 유지된다.
  function bindTabs() {
    document.querySelectorAll('[data-tabs]').forEach(function (tabs) {
      var buttons = tabs.querySelectorAll('button[data-tab]');
      var ink = document.createElement('span');
      ink.className = 'tabs-ink';
      tabs.appendChild(ink);

      function moveInk(btn) {
        ink.style.width = btn.offsetWidth + 'px';
        ink.style.transform = 'translateX(' + btn.offsetLeft + 'px)';
      }

      function select(name, animate) {
        var found = false;
        buttons.forEach(function (b) {
          var on = b.getAttribute('data-tab') === name;
          b.classList.toggle('on', on);
          if (on) { moveInk(b); found = true; }
        });
        if (!found) return false;
        document.querySelectorAll('[data-panel]').forEach(function (p) {
          var on = p.getAttribute('data-panel') === name;
          p.hidden = !on;
          p.classList.remove('entering');
          if (on && animate && !reduceMotion) {
            void p.offsetWidth;
            p.classList.add('entering');
          }
        });
        return true;
      }

      buttons.forEach(function (b) {
        b.addEventListener('click', function () {
          var name = b.getAttribute('data-tab');
          select(name, true);
          history.replaceState(null, '', '#' + name);
        });
      });

      var initial = location.hash.replace('#', '');
      if (!initial || !select(initial, false)) {
        select(buttons[0].getAttribute('data-tab'), false);
      }
      window.addEventListener('resize', function () {
        var on = tabs.querySelector('button.on');
        if (on) moveInk(on);
      });
    });
  }

  // 세그먼트 컨트롤의 흰 배경이 선택된 칸으로 움직인다.
  function bindSegments() {
    document.querySelectorAll('.seg').forEach(function (seg) {
      var on = seg.querySelector('.on');
      if (!on) return;
      var thumb = document.createElement('span');
      thumb.className = 'seg-thumb';
      seg.insertBefore(thumb, seg.firstChild);
      function place(el) {
        thumb.style.width = el.offsetWidth + 'px';
        thumb.style.transform = 'translateX(' + el.offsetLeft + 'px)';
      }
      place(on);
      seg.querySelectorAll('a, button').forEach(function (el) {
        el.addEventListener('click', function () {
          seg.querySelectorAll('.on').forEach(function (x) { x.classList.remove('on'); });
          el.classList.add('on');
          place(el);
        });
      });
    });
  }

  window.OnoAdmin = {
    toast: function (message) {
      var t = document.createElement('div');
      t.className = 'toast';
      t.textContent = message;
      document.body.appendChild(t);
      requestAnimationFrame(function () { t.classList.add('show'); });
      setTimeout(function () {
        t.classList.remove('show');
        setTimeout(function () { t.remove(); }, 300);
      }, 2200);
    },
    openModal: function (id) { document.getElementById(id).classList.add('open'); },
    closeModal: function (id) { document.getElementById(id).classList.remove('open'); }
  };

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      document.querySelectorAll('.modal.open').forEach(function (m) { m.classList.remove('open'); });
    }
  });

  document.addEventListener('DOMContentLoaded', function () {
    bindRowLinks();
    reveal();
    countUp();
    bindTabs();
    bindSegments();
  });
})();
