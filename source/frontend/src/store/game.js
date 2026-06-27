import { defineStore } from 'pinia'

export const useGameStore = defineStore('game', {
  state: () => ({
    room: null,
    currentRoomCode: localStorage.getItem('sgs-room') || ''
  }),
  actions: {
    setRoom(room) {
      this.room = room
      this.currentRoomCode = room?.roomCode || ''
      if (this.currentRoomCode) {
        localStorage.setItem('sgs-room', this.currentRoomCode)
      }
    },
    clearRoom() {
      this.room = null
      this.currentRoomCode = ''
      localStorage.removeItem('sgs-room')
    }
  }
})
