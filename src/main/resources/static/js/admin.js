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

  // 화면이 뜬 뒤에 카드를 투명하게 만들었다 다시 올리거나 숫자를 0 으로 되돌렸다 세면,
  // 이동할 때마다 한 번 그려진 화면이 사라졌다 다시 나타나 깜빡이는 것처럼 보였다. 그래서 첫 화면은 그대로 둔다.

  // 사이드바 접기와 펼치기. 데스크톱은 접힘 상태를 기억하고, 모바일은 서랍처럼 열고 닫는다.
  function bindNav() {
    var root = document.documentElement;
    var mobile = window.matchMedia('(max-width: 860px)');

    function remember(collapsed) {
      try { localStorage.setItem('ono-admin-nav', collapsed ? 'collapsed' : 'open'); } catch (e) {}
    }

    document.querySelectorAll('[data-nav-open]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        if (mobile.matches) {
          root.classList.add('nav-open');
        } else {
          root.classList.remove('nav-collapsed');
          remember(false);
        }
      });
    });
    document.querySelectorAll('[data-nav-close]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        if (mobile.matches) {
          root.classList.remove('nav-open');
        } else {
          root.classList.add('nav-collapsed');
          remember(true);
        }
      });
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') root.classList.remove('nav-open');
    });
    // 화면 폭이 바뀌어 서랍이 필요 없어지면 열린 서랍 상태를 지운다.
    mobile.addEventListener('change', function () { root.classList.remove('nav-open'); });
    // 뒤로 가기로 돌아왔을 때 서랍이 열린 채로 보이지 않게 한다.
    window.addEventListener('pageshow', function () { root.classList.remove('nav-open'); });

    // 첫 화면이 그려진 다음에야 움직임을 켠다. 그래야 새로고침할 때 사이드바가 미끄러지지 않는다.
    requestAnimationFrame(function () {
      requestAnimationFrame(function () { root.classList.add('nav-ready'); });
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

  // 이 스크립트는 body 맨 끝에서 읽히므로 화면 요소가 이미 다 있다. DOMContentLoaded 까지 기다리면
  // 모든 탭 패널이 한 번 그려졌다가 숨겨져서 깜빡이므로 바로 붙인다.
  function init() {
    bindRowLinks();
    bindNav();
    bindTabs();
    bindSegments();
  }
  if (document.readyState === 'loading' && !document.querySelector('.shell, .login')) {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
