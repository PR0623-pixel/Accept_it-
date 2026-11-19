/**
 * mockServer.ts — 백엔드 없이 UI를 테스트하기 위한 "가짜 서버" (브라우저 메모리에서 동작)
 *
 * [BACKEND] 이 파일의 send() 가 곧 "게임 규칙 명세서"입니다.
 *   서버를 구현할 때 이 파일의 switch 문 순서/검증/에러 코드를 그대로 옮기면 프론트와 바로 맞물립니다.
 *   (실제 서버에서는 방마다 상태를 들고 있고, 수신자별로 ROOM_STATE 를 마스킹해서 보내야 합니다.)
 *
 * [MOCK ONLY] 방 1개만 존재하고, 한 브라우저에서 3명 역할을 모두 테스트할 수 있도록
 *   방을 만들면 가짜 플레이어 2명이 자동으로 들어옵니다. (DevBar 로 시점 전환)
 */
import type { Card, ChatMessage, ClientEvent, Item, Player, RoomState, ServerEvent } from '../types';
import { REBUTTAL_SECONDS, REQUIRED_PLAYERS, SETTINGS_LIMITS, SPEECH_SECONDS } from '../types';

const ITEMS: Omit<Item, 'index'>[] = [
  { name: '전기 통근버스 100대', description: '공공기관 통근버스를 전기버스로 교체합니다. 친환경 vs 초기 비용 부담' },
  { name: '학교 급식용 친환경 식자재', description: '전국 학교 급식에 친환경 식자재를 일괄 조달합니다. 품질 vs 단가' },
  { name: '중소기업 혁신제품 우선구매', description: '혁신제품을 수의계약으로 우선 구매합니다. 육성 vs 공정경쟁' },
  { name: '비축 마스크 500만 장', description: '감염병 대비 비축물자로 마스크를 확보합니다. 안보 vs 보관 비용' },
  { name: 'AI 민원 챗봇 시스템', description: '공공기관 민원 응대용 AI 챗봇을 조달합니다. 효율 vs 안전성' },
];

const KEYWORDS: [string, string][] = [
  ['가격', '최저가 낙찰이 항상 최선일까요?'], ['품질', '싸게 샀다가 고장 나면 누가 책임질까요?'],
  ['납기', '제때 공급되지 않으면 행정이 멈춥니다.'], ['중소기업', '중소기업 판로 확대와 직결됩니다.'],
  ['투명성', '모든 입찰 과정은 공개되어야 합니다.'], ['친환경', '장기적으로 환경 비용을 줄입니다.'],
  ['예산', '세금은 한정되어 있습니다.'], ['안전', '국민 안전이 최우선입니다.'],
  ['공정경쟁', '모두에게 같은 기회가 주어져야 합니다.'], ['혁신', '새로운 기술은 시장을 키웁니다.'],
  ['나라장터', '전자조달의 중심 플랫폼입니다.'], ['비상대응', '위기에서는 속도가 곧 생명입니다.'],
];

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));
const P = (id: string, nickname: string, isHost = false): Player => ({
  id, nickname, role: 'NONE', ready: false, stance: null, hasVoted: false, isHost,
});

type Listener = (e: ServerEvent) => void;

class MockServer {
  private room: RoomState | null = null;
  private hands = new Map<string, Card[]>();
  private chat: ChatMessage[] = [];
  private items: Item[] = [];
  private listener: Listener | null = null;
  private viewerId = '';
  private seq = 1;
  private ticker?: number;
  private pendingCard: { playerId: string; cardId: number } | null = null; // 반론에 쓴 카드 (묵살 시 환원용)

  // ---- REST 흉내 -----------------------------------------------------------
  createRoom(nickname: string) {
    this.makeRoom('ABC123', [P('p1', nickname, true), P('p2', '민수'), P('p3', '홍석')]);
    return { roomCode: 'ABC123', playerId: 'p1' };
  }
  joinRoom(code: string, nickname: string) {
    if (!this.room || this.room.roomCode !== code) {
      this.makeRoom(code, [P('p1', '방장', true), P('p2', nickname), P('p3', '홍석')]);
      return { roomCode: code, playerId: 'p2' };
    }
    if (this.room.players.length >= REQUIRED_PLAYERS) throw new Error('방이 가득 찼습니다.');
    const id = `p${this.room.players.length + 1}`;
    this.room.players.push(P(id, nickname));
    return { roomCode: code, playerId: id };
  }

  // ---- WebSocket 흉내 ------------------------------------------------------
  connect(playerId: string, l: Listener) {
    this.viewerId = playerId;
    this.listener = l;
    if (!this.room) return l({ type: 'ERROR', payload: { code: 'ROOM_NOT_FOUND', message: '방을 찾을 수 없습니다.' } });
    l({ type: 'ROOM_STATE', payload: this.view() });
    l({ type: 'HAND', payload: structuredClone(this.hands.get(playerId) ?? []) });
    l({ type: 'CHAT_HISTORY', payload: structuredClone(this.chat) });
  }
  disconnect() { this.listener = null; }

  // ---- 내부 도우미 ---------------------------------------------------------
  private makeRoom(code: string, players: Player[]) {
    this.hands.clear(); this.chat = [];
    this.room = {
      roomCode: code, serverTime: Date.now(), phase: 'LOBBY', players,
      settings: { maxItems: 3, itemSeconds: 300, cardsPerItem: 3 },
      currentItem: null, itemEndsAt: null, speech: null, lastSpeakerId: null,
      pendingRebuttal: null, effect: null, results: [],
    };
    window.clearInterval(this.ticker);
    this.ticker = window.setInterval(() => this.tick(), 500);
  }
  /** 수신자(viewer)별 스냅샷: VOTING 중 타인의 stance 마스킹 */
  private view(): RoomState {
    const r = structuredClone(this.room!);
    r.serverTime = Date.now();
    if (r.phase === 'VOTING') r.players.forEach((p) => { if (p.id !== this.viewerId) p.stance = null; });
    return r;
  }
  private emit() {
    this.listener?.({ type: 'ROOM_STATE', payload: this.view() });
    this.listener?.({ type: 'HAND', payload: structuredClone(this.hands.get(this.viewerId) ?? []) });
  }
  private fail(code: string, message: string) { this.listener?.({ type: 'ERROR', payload: { code, message } }); }
  private say(text: string) { this.push({ kind: 'SYSTEM', text }); }
  private push(m: Omit<ChatMessage, 'id' | 'createdAt'>) {
    const msg = { ...m, id: String(this.seq++), createdAt: Date.now() };
    this.chat.push(msg);
    this.listener?.({ type: 'CHAT', payload: msg });
  }
  private takeCard(pid: string, cardId: number) {
    const c = this.hands.get(pid)?.find((x) => x.cardId === cardId && !x.used);
    if (c) c.used = true;
    return c;
  }
  private deal(n: number): Card[] {
    return [...KEYWORDS].sort(() => Math.random() - 0.5).slice(0, n).map(([keyword, description]) => ({
      cardId: this.seq++, keyword, title: `${keyword} 카드`, description, used: false,
    }));
  }
  private startItem(index: number) {
    const r = this.room!;
    r.phase = 'VOTING'; r.currentItem = this.items[index - 1]; r.itemEndsAt = null;
    r.speech = null; r.lastSpeakerId = null; r.pendingRebuttal = null; this.pendingCard = null;//물품이 바뀔 때 초기화
    r.players.forEach((p) => { p.stance = null; p.hasVoted = false; });
    this.chat = [];
    this.listener?.({ type: 'CHAT_HISTORY', payload: [] });
    this.say(`물품 ${index}/${r.settings.maxItems}: ${r.currentItem.name} — 찬성/반대를 선택하세요.`);
    
  }
  /** 객원 중 한 명이라도 키워드 카드를 모두 소진하면 시간과 상관없이 사회자 결정으로 넘어간다.
 *  발언을 끊지 않도록 "발언권이 비는 순간"에만 검사한다. */
  private checkExhausted(): boolean {
    const r = this.room!;
    if (r.phase !== 'DEBATE' || r.speech || r.pendingRebuttal) return false;
    const out = r.players.find((p) => p.role === 'GUEST' && (this.hands.get(p.id) ?? []).every((c) => c.used));
    if (!out) return false;
    r.phase = 'MODERATOR_DECISION';
    this.say(`${out.nickname}님이 키워드 카드를 모두 사용했습니다. 사회자의 결정으로 넘어갑니다.`);
    return true;
  }
  /** [BACKEND] 서버에서는 @Scheduled 또는 방별 타이머로 구현. 시간 종료는 서버가 직접 판단해야 합니다. */
  private tick() {
    const r = this.room; if (!r) return;
    const now = Date.now(); let changed = false;
    // 변경 전
    //if (r.speech && now >= r.speech.endsAt) { r.speech = null; changed = true; }
    // 변경 후
    if (r.speech && now >= r.speech.endsAt) { r.speech = null; this.checkExhausted(); changed = true; }
    if (r.phase === 'DEBATE' && r.itemEndsAt && now >= r.itemEndsAt) {
      r.phase = 'MODERATOR_DECISION'; r.speech = null; r.pendingRebuttal = null;
      this.say('시간 종료! 사회자가 물품의 입건 여부를 결정합니다.'); changed = true;
    }
    if (changed) this.emit();
  }

  // ---- 클라이언트 이벤트 처리 (= 게임 규칙) --------------------------------
  send(ev: ClientEvent) {
    const r = this.room;
    const me = r?.players.find((p) => p.id === this.viewerId);
    if (!r || !me) return;
    const guestCount = () => r.players.filter((p) => p.role === 'GUEST');

    switch (ev.type) {
      case 'UPDATE_SETTINGS': {
        if (!me.isHost) return this.fail('NOT_HOST', '방장만 설정을 바꿀 수 있습니다.');
        if (r.phase !== 'LOBBY') return;
        const L = SETTINGS_LIMITS, s = ev.payload;
        r.settings = {
          maxItems: clamp(s.maxItems, L.maxItems.min, L.maxItems.max),
          itemSeconds: clamp(s.itemSeconds, L.itemSeconds.min, L.itemSeconds.max),
          cardsPerItem: clamp(s.cardsPerItem, L.cardsPerItem.min, L.cardsPerItem.max),
        };
        r.players.forEach((p) => (p.ready = false)); // 설정이 바뀌면 준비 해제
        break;
      }
      case 'SET_ROLE': {
        if (r.phase !== 'LOBBY') return;
        const { role } = ev.payload;
        const cap = role === 'MODERATOR' ? 1 : role === 'GUEST' ? 2 : 99;
        if (r.players.filter((p) => p.role === role && p.id !== me.id).length >= cap)
          return this.fail('ROLE_FULL', '해당 역할 자리가 가득 찼습니다.');
        me.role = role; me.ready = false;
        break;
      }
      case 'SET_READY': {
        if (r.phase !== 'LOBBY') return this.fail('ALREADY_STARTED', '대기 중인 로비에서만 준비할 수 있습니다.');
        if (ev.payload.ready && me.role === 'NONE') {
          return this.fail('ROLE_REQUIRED', '역할 칸으로 먼저 이동하세요.');
        }

        // Ready는 개별 의사 표시입니다. 인원/역할/전원 Ready 검증은 START_GAME에서 합니다.
        me.ready = ev.payload.ready;
        break;
      }

      case 'START_GAME': {
        if (r.phase !== 'LOBBY') {
          return this.fail('ALREADY_STARTED', '이미 시작된 방입니다.');
        }
        if (!me.isHost) {
          return this.fail('NOT_HOST', '방장만 게임을 시작할 수 있습니다.');
        }
        if (r.players.length < REQUIRED_PLAYERS) {
          return this.fail('NOT_ENOUGH_PLAYERS', '3명이 모여야 게임을 시작할 수 있습니다.');
        }
        if (r.players.length !== REQUIRED_PLAYERS) {
          return this.fail('INVALID_PLAYER_COUNT', '참가자는 정확히 3명이어야 합니다.');
        }

        const moderatorCount = r.players.filter((p) => p.role === 'MODERATOR').length;
        const guestCount = r.players.filter((p) => p.role === 'GUEST').length;
        if (moderatorCount !== 1 || guestCount !== 2) {
          return this.fail('INVALID_ROLE_COMPOSITION', '사회자 1명과 객원 2명이 필요합니다.');
        }
        if (r.players.some((p) => !p.ready)) {
          return this.fail('NOT_ALL_READY', '모든 참가자가 Ready 상태여야 합니다.');
        }

        this.items = [...ITEMS]
          .sort(() => Math.random() - 0.5)
          .slice(0, r.settings.maxItems)
          .map((item, index) => ({ ...item, index: index + 1 }));
        r.results = [];
        this.startItem(1);
        break;
      }
      case 'VOTE': {
        if (r.phase !== 'VOTING' || me.role !== 'GUEST') return this.fail('INVALID', '지금은 선택할 수 없습니다.');
        if (me.hasVoted) return this.fail('ALREADY_VOTED', '이미 선택했습니다.');
        me.stance = ev.payload.stance; me.hasVoted = true;
        this.say(`${me.nickname}님이 선택을 마쳤습니다.`);
        const [a, b] = guestCount();
        if (a?.hasVoted && b?.hasVoted) {
          if (a.stance === b.stance) {
            r.phase = 'MODERATOR_DECISION';
            this.say('두 객원의 의견이 일치합니다. 사회자의 결정으로 넘어갑니다.');
          } else {
            r.phase = 'DEBATE';
            r.itemEndsAt = Date.now() + r.settings.itemSeconds * 1000;

            // 투표 후 진영이 결정된 다음 객원에게 손패를 줍니다.
            this.hands.clear();
            for (const guest of [a, b]) {
              this.hands.set(guest.id, this.deal(r.settings.cardsPerItem));
            }

            this.say('의견이 갈렸습니다. 토론을 시작하세요!');
          }
        }
        break;
      }
      case 'START_SPEECH': {
        if (r.phase !== 'DEBATE' || me.role !== 'GUEST') return this.fail('INVALID', '지금은 발언할 수 없습니다.');
        if (r.speech || r.pendingRebuttal) return this.fail('FLOOR_BUSY', '다른 사람이 발언 중입니다.');
        if (r.lastSpeakerId === me.id) return this.fail('CONSECUTIVE_SPEECH', '직전 발언자는 연속으로 발언할 수 없습니다.');
        const card = this.takeCard(me.id, ev.payload.cardId);
        if (!card) return this.fail('CARD_REQUIRED', '사용할 키워드 카드를 선택하세요.');
        r.speech = { speakerId: me.id, kind: 'SPEECH', endsAt: Date.now() + SPEECH_SECONDS * 1000, keyword: card.keyword };
        r.lastSpeakerId = me.id;
        this.push({ kind: 'SPEECH', senderId: me.id, nickname: me.nickname, stance: me.stance ?? undefined, text: '(발언 시작)', keyword: card.keyword });
        break;
      }
      case 'SEND_CHAT': {
        const text = ev.payload.text.trim().slice(0, 200);
        if (!text) return;
        if (me.role === 'MODERATOR') {
          // 사회자는 발언권과 상관없이 게임 중 언제든 채팅 가능
          if (r.phase === 'LOBBY' || r.phase === 'RESULT') return this.fail('INVALID', '게임 중에만 채팅할 수 있습니다.');
          this.push({ kind: 'MODERATOR', senderId: me.id, nickname: me.nickname, text });
          return;
        }
        if (!r.speech || r.speech.speakerId !== me.id) return this.fail('NOT_YOUR_TURN', '발언권이 있을 때만 말할 수 있습니다.');
        this.push({ kind: r.speech.kind, senderId: me.id, nickname: me.nickname, stance: me.stance ?? undefined, text, keyword: r.speech.keyword });
        return; // 상태 변화 없음 → ROOM_STATE 재전송 불필요
      }
      case 'END_SPEECH': {
        if (r.speech?.speakerId === me.id) { r.speech = null; this.checkExhausted(); }
        break;
      }
      case 'REQUEST_REBUTTAL': {
        if (r.phase !== 'DEBATE' || me.role !== 'GUEST') return this.fail('INVALID', '지금은 반론할 수 없습니다.');
        if (!r.speech || r.speech.kind !== 'SPEECH' || r.speech.speakerId === me.id) return this.fail('NO_TARGET', '반론할 상대의 발언이 없습니다.');
        if (r.pendingRebuttal) return this.fail('PENDING', '이미 반론 요청이 진행 중입니다.');
        const card = this.takeCard(me.id, ev.payload.cardId);
        if (!card) return this.fail('CARD_REQUIRED', '반론에 쓸 키워드 카드를 선택하세요.');
        r.pendingRebuttal = { requesterId: me.id, targetId: r.speech.speakerId, keyword: card.keyword };
        this.pendingCard = { playerId: me.id, cardId: card.cardId }; // 묵살되면 이 카드를 돌려준다
        this.push({ kind: 'REBUTTAL', senderId: me.id, nickname: me.nickname, stance: me.stance ?? undefined, text: '(반론 요청 — 사회자 판단 대기)', keyword: card.keyword });
        break;
      }
      case 'DECIDE_REBUTTAL': {
        const p = r.pendingRebuttal;
        if (me.role !== 'MODERATOR' || !p) return this.fail('INVALID', '결정할 반론 요청이 없습니다.');
        r.pendingRebuttal = null;
        const pc = this.pendingCard; this.pendingCard = null;                                // ← 추가
        if (ev.payload.approve) {
          r.speech = { speakerId: p.requesterId, kind: 'REBUTTAL', endsAt: Date.now() + REBUTTAL_SECONDS * 1000, keyword: p.keyword };
          r.lastSpeakerId = p.requesterId;
          r.effect = { id: this.seq++, type: 'REBUTTAL_APPROVED', targetId: p.requesterId };
          this.say('사회자가 반론을 승인했습니다. 반론자에게 발언권 1분!');
        } else {
          r.effect = { id: this.seq++, type: 'REBUTTAL_DENIED', targetId: p.requesterId };
          const back = pc && this.hands.get(pc.playerId)?.find((x) => x.cardId === pc.cardId); // ← 추가
          if (back) back.used = false;                                                         // ← 추가 (카드 환원)
          this.say('사회자가 반론을 묵살했습니다. 💢 (사용한 키워드 카드는 돌려받습니다)');   // ← 문구 변경
          this.checkExhausted();                                                               // ← 추가
        }
        break;
      }
      case 'DECIDE_ITEM': {
        if (me.role !== 'MODERATOR' || r.phase !== 'MODERATOR_DECISION') return this.fail('INVALID', '지금은 결정할 수 없습니다.');
        const { accept } = ev.payload, item = r.currentItem!;
        r.results.push({ itemName: item.name, accepted: accept });
        r.effect = { id: this.seq++, type: accept ? 'ITEM_ACCEPTED' : 'ITEM_REJECTED' };
        if (item.index >= r.settings.maxItems) { r.phase = 'RESULT'; r.currentItem = null; r.speech = null; r.itemEndsAt = null; }
        else this.startItem(item.index + 1);
        break;
      }
    }
    this.emit();
  }
}

export const mockServer = new MockServer();
